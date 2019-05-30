# GroupMessenger_TOTALFIFO
Group messenger built in Android that guarantees both TOTAL and FIFO ordering.

**Total Ordering:**

Guarantees every process delivers all messages in the same order. So long as every process follows the same order, causal or temporal relationships between messages do not matter.

**FIFO Ordering:**

Each process delivers messages in the order in which they are received. Note that processes may deliver messsages in a different ordering relative to one another.

**TOTAL-FIFO Ordering:**

All processes preserve the message sending order of one another and every process delivers all messages in the same order.

This app implements a modified ISIS algorithm that guarantees both TOTAL and FIFO ordering, even under process failure. See [here](https://github.com/Cabbler25/GroupMessenger_TOTALFIFO/blob/master/README.pdf) for in-depth project specifications. Credits to Steve Ko, Professor at the University at Buffalo, for contents of the PDF and testing scripts.


**For testing/running the grader (Windows machine required)**
>Open the project in Android Studio to generate the apk file.

>Download the tools found [here.](https://github.com/Cabbler25/GroupMessenger_TOTALFIFO/tree/master/Testing_Tools)

>Use the command *python create_avd.py 5* to create testing AVD's.

>Use the command *python run_avd.py 5* to run the AVD's.

>Use the command *python set_redir.py 10000* to set up their networking.

>Ensure the app is installed on all 5 AVD's before running the grader.

>Use the command groupmessenger2-grading.exe apk_file_path to run the grader. Note -h will show you options available to the grader.
