package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.SpannableString;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Menu;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.PriorityQueue;


/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 */
public class GroupMessengerActivity extends Activity {

    static final Uri M_URI = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
    static final String[] CLIENT_PORTS = {"11108", "11112", "11116", "11120", "11124"};
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final String COMPLETED = "1";
    static final char SPLITTER = '|';
    static final int SERVER_PORT = 10000;
    static final int TIMEOUT_DELAY = 2000;

    static PriorityQueue<Message> queue = new PriorityQueue<Message>();
    static String localPort;
    static int seqNum = -1;

    class Message implements Comparable<Message> {
        String msg, priority, senderPort;
        boolean deliverable = false;

        Message(String proposedPriority, String senderPort, String msg) {
            this.priority = proposedPriority;
            this.senderPort = senderPort;
            this.msg = msg;
        }

        private void setDeliverable(boolean val) {
            deliverable = val;
        }

        @Override
        public int compareTo(Message other) {
            if (deliverable == other.deliverable) {
                return Integer.parseInt(other.priority) > Integer.parseInt(priority) ? -1 : 1;
            } else {
                return other.deliverable && !deliverable ? 1 : -1;
            }
        }
    }

    private static Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    // Split line into 4 sections based on |
    // bool|seqNum|port|message
    private String[] splitInput(String line) {
        String[] arr = new String[4];
        int count = 0;
        arr[count] = "";

        for (int i = 0; i < line.length(); i++) {
            if (count == arr.length - 1) {
                arr[count] += line.charAt(i);
            } else if (line.charAt(i) == SPLITTER) {
                count++;
                arr[count] = "";
            } else {
                arr[count] += line.charAt(i);
            }
        }

        return arr;
    }

    private int getTextColor(String clientPort) {
        int local = Integer.parseInt(localPort);
        int client = Integer.parseInt(clientPort);
        if (local == client) return Color.BLACK;

        switch (client) {
            case 11108:
                return Color.RED;
            case 11112:
                return Color.MAGENTA;
            case 11116:
                return Color.CYAN;
            case 11120:
                return Color.GREEN;
            case 11124:
                return Color.BLUE;
            default:
                return Color.BLACK;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);
        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create server socket");
            e.printStackTrace();
        }

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        localPort = String.valueOf((Integer.parseInt(portStr) * 2));

        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        findViewById(R.id.button4).setOnClickListener(v -> {
            EditText editText = (EditText) findViewById(R.id.editText1);
            String msg = editText.getText().toString();
            editText.setText("");
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg);
        });
    }

    private class ServerTask extends AsyncTask<ServerSocket, Message, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            String clientPort = null;
            Socket client = null;
            try {
                while (true) {
                    // Read in initial message from user
                    client = serverSocket.accept();
                    BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                    String msg = in.readLine();
                    String[] params = splitInput(msg);

                    // Send proposed seqNum
                    seqNum++;
                    PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                    out.println(Integer.toString(seqNum));

                    // Add to the queue
                    clientPort = params[2];
                    msg = params[3];
                    Message message = new Message(Integer.toString(seqNum), clientPort, msg);
                    queue.add(message);

                    // Get approved seqNum from user
                    msg = in.readLine();
                    if (msg != null) {
                        params = splitInput(msg);
                        if (!Boolean.parseBoolean(params[0])) throw new SocketTimeoutException();
                        seqNum = Integer.parseInt(params[1]);
                        out.println(COMPLETED);

                        // Re-sort our queue with new seqNum
                        queue.remove(message);
                        message.priority = params[1];
                        message.setDeliverable(true);
                        queue.add(message);

                        // Deliver all deliverable items in the queue
                        while (!queue.isEmpty() && queue.peek().deliverable) {
                            publishProgress(queue.poll());
                        }
                    }
                }
            } catch (SocketTimeoutException e) {
                Log.e(TAG, "Socket timed out");

                // Remove all timed out items corresponding to this client port
                PriorityQueue<Message> temp = new PriorityQueue<>();
                while (!queue.isEmpty()) {
                    Message msg = queue.poll();
                    if (!msg.senderPort.equals(clientPort)) {
                        temp.add(msg);
                    }
                }
                queue = temp;
                closeSocket(client);
            } catch (SocketException e) {
                Log.e(TAG, "Socket exception");
                closeSocket(client);
            } catch (IOException e) {
                Log.e(TAG, "IOException");
                closeSocket(client);
            }
            return null;
        }

        private void closeSocket(Socket socket) {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        protected void onProgressUpdate(Message... messages) {
            Message message = messages[0];
            if (message == null) return;

            ContentValues val = new ContentValues();
            val.put("key", message.priority);
            val.put("value", message.msg);
            getContentResolver().insert(M_URI, val);

            SpannableString text = new SpannableString(message.senderPort + ": " + message.msg + "\n\n");
            text.setSpan(new ForegroundColorSpan(getTextColor(message.senderPort)), 0, text.length(), 0);
            ((TextView) findViewById(R.id.textView1)).append(text);
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            Socket[] sockets = new Socket[CLIENT_PORTS.length];
            String msgToSend = msgs[0];
            int maxPriority = 0;

            // Get max seqNum
            for (int i = 0; i < sockets.length; i++) {
                try {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(CLIENT_PORTS[i]));
                    socket.setSoTimeout(TIMEOUT_DELAY);
                    sockets[i] = socket;

                    // Write the message
                    // Message ordering - bool|seqNum|port|message
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    out.println("false|00|" + localPort + "|" + msgToSend);

                    // Read proposed seqNum and compare it to current max
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String response = in.readLine();
                    if (response != null) {
                        maxPriority = Math.max(maxPriority, Integer.parseInt(response));
                    } else {
                        Log.e(TAG, "No response from server: " + CLIENT_PORTS[i]);
                    }
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // Max seqNum acquired, rebroadcast message
            for (Socket socket : sockets) {
                try {
                    // Write the message, indicating message is now deliverable
                    // along with acquired max seqNum
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    out.println("true|" + Integer.toString(maxPriority) + "|" + localPort + "|" + msgToSend);

                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String response = in.readLine();
                    if (response != null) {
                        Log.d(TAG, "\"" + msgToSend + "\" delivered successfully");
                    } else {
                        Log.e(TAG, "No response from server: " + socket.getPort());
                    }

                    socket.close();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }
}
