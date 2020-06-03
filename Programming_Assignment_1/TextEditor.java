import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
/*
  21602486 - Fatih Sevban Uyanık
  21503061 - Ata Coşkun
*/
public class TextEditor {

    // properties
    public static Socket clientSocket ;
    public static BufferedReader inputFromServer;
    public static BufferedReader inputFromUser;
    public static DataOutputStream outputToServer;

    public static void main(String[] args) {

        String ip_adress = "127.0.0.1";
        String port = "60000";

        if(args.length > 0 && !args[0].isEmpty()) { ip_adress =  args[0]; }
        if(args.length > 1 && !args[1].isEmpty()) { port = args[1]; }
        int port_number = Integer.parseInt(port);
        setupSocket(ip_adress,port_number);

        // ================================================
        // Authentication
        // ================================================

        System.out.println();
        System.out.println("----------------------");
        System.out.println("--- Authentication ---");
        System.out.println("----------------------");


        System.out.println("Please enter the username command --> USER <username>");
        String auth_string = getUserInput();
        authenticateUsername(auth_string);

        System.out.println("Please enter the password comment --> PASS <password>");
        String auth_string_pass = getUserInput();
        authenticatePassword(auth_string_pass);
        System.out.println("Authentication Successful.");

        // ================================================
        // Request Update
        // ================================================

        System.out.println();

        while (true) {
            System.out.println("Please get an update first by typing the update command --> UPDT <version>");
            String command = getUserInput();
            String[] arguments = command.split(" ");
            String choice = arguments[0];

            if (choice.equals("UPDT") && arguments.length > 1) {
                updateDocument(command, false);
                break;
            }
        }

        // ================================================
        // File Transactions
        // ================================================

        System.out.println();
        System.out.println("-------------------------");
        System.out.println("--- File Transactions ---");
        System.out.println("-------------------------");
        boolean isExited = false;

        do {
            System.out.println("UPDT --> UPDT <version>");
            System.out.println("APND --> APND <version><space>“<text>”");
            System.out.println("WRTE --> WRTE <version><space><linenumber><space> “<text>”");
            System.out.println("EXIT --> EXIT");
            System.out.print("Type your command: ");

            String command = getUserInput();
            String[] arguments = command.split(" ");
            String choice = arguments[0];

            switch (choice) {
                case "UPDT":
                    if (isValidArgument(command)) { updateDocument(command, false); }
                    break;
                case "APND": {
                    if (isValidArgument(command)) { appendDocument(command); }
                    break;
                }
                case "WRTE": {
                    if (isValidArgument(command)) { writeDocument(command); }
                    break;
                }
                case "EXIT":
                    if (isValidArgument(command)) { isExited = exit(); }
                    break;
            }

            System.out.println("-------------------------");

        } while (!isExited);

        try {
            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean isValidArgument(String command){
        String[] splitted_command = command.split(" ");
        String check_string = splitted_command[0];

        if(check_string.equals ("UPDT")){

            if(splitted_command.length == 2) return true;
        }

        else if(check_string.equals("APND") ){
            int index_bracket = command.indexOf('"');

            if(index_bracket == -1){
                System.out.println("APND command consists \" character, please enter the command in the correct format");
                return false;
            }
            else{
                String check_arg = command.substring(0,index_bracket-1);
                String[] sp_arg = check_arg.split(" ");
                if(sp_arg.length !=2){
                    System.out.println("Please enter the command in the correct format");
                    return false;
                }
                else
                    return true;
            }
        }

        else if(check_string.equals ("WRTE")){

            int index_bracket = command.indexOf('"');

            if(index_bracket == -1){
                System.out.println("WRTE command consists \" character, please enter the command in the correct format");
                return false;
            }

            else{
                String check_arg = command.substring(0,index_bracket-1);
                String[] sp_arg = check_arg.split(" ");

                if(sp_arg.length !=3){
                    System.out.println("Please enter the command in the correct format");
                    return false;
                }
                else
                    return true;
            }

        }
        else if(check_string.equals("EXIT")){
            if(splitted_command.length == 1) return true;
        }
        else
            System.out.println("Please enter the command in the correct format");
        return false;

    }
    private static void writeDocument(String command) {
        String[] splitted_command = command.split(" ");
        int index = command.indexOf('"');
        String text = command.substring(index+1, command.length()-1);
        String commandWrite = splitted_command[0] + " " + splitted_command[1] + " " +
                splitted_command[2] + " " + text + "\r\n";

        try {
            // sending text to server.
            outputToServer.writeBytes(commandWrite);
            String response = getServerResponse();
            String[] responseArr = response.split(" ");
            boolean isValid = responseArr[0].contains("OK");
            updateDocument("UPDT 0", true);

            if (isValid) {
                int currentVersion = Integer.parseInt(responseArr[1]);
                System.out.println("Write operation successfully done. ---> Current Version:  " +currentVersion);
            } else if (response.contains("INVALID")) {
                System.out.println("Write operation was unsuccessful.(Version Conflict)");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean exit() {
        String commandExit = "EXIT"+"\r\n";

        try {
            // sending text to server.
            outputToServer.writeBytes(commandExit);
            String response = getServerResponse();
            String[] responseArr = response.split(" ");
            boolean isValid = responseArr[0].contains("OK");

            if (isValid) {
                System.out.println("Exited, Terminating..." );
                return true;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }

    private static void appendDocument(String command) {
        String[] splitted_command = command.split(" ");
        int index = command.indexOf('"');
        String text = command.substring(index+1, command.length()-1);
        String commandWrite = splitted_command[0] + " " + splitted_command[1] + " " + text +"\r\n";

        try {
            // sending text to server.
            outputToServer.writeBytes(commandWrite);
            String response = getServerResponse();
            String[] responseArr = response.split(" ");
            boolean isValid = responseArr[0].contains("OK");

            if (isValid) {
                int currentVersion = Integer.parseInt(responseArr[1]);
                System.out.println("Document was Appended. ---> Current Version: " + currentVersion);
            } else if (response.contains("INVALID")) {
                System.out.println("Document Append operation was unsuccessful.(Version Conflict)");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void updateDocument(String command, boolean isWrite) {
        String commandUpdate = command +"\r\n";

        try {
            // sending version to server.
            outputToServer.writeBytes(commandUpdate);
            String responseUpdate = getServerResponse();

            // parsing response
            if (!isWrite) {
                String[] responseArr = responseUpdate.split(" ");
                int currentVersion = Integer.parseInt(responseArr[1]);
                System.out.println("Current Version: " + currentVersion);
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void setupSocket(String ip_adress, int port) {
        try {
            clientSocket = new Socket(ip_adress, port);
            InputStream inputStream = clientSocket.getInputStream();
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            inputFromServer = new BufferedReader(inputStreamReader);
            inputFromUser = new BufferedReader(new InputStreamReader(System.in));
            outputToServer = new DataOutputStream(clientSocket.getOutputStream());
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getUserInput() {
        String input = "";
        try { input = inputFromUser.readLine(); }
        catch (IOException e) { e.printStackTrace(); }
        return input;
    }

    public static String getServerResponse() {
        StringBuilder stringBuilder = new StringBuilder();

        try {
            int character;
            while((character = inputFromServer.read()) != 13) {
                stringBuilder.append((char) character);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        stringBuilder.trimToSize();
        return stringBuilder.toString();
    }


    public static void authenticateUsername(String command) {
        String commandUsername = command + "\r\n";

        try {
            // sending password to server.
            outputToServer.writeBytes(commandUsername);
            String responsePassword = getServerResponse();

            if (responsePassword.contains("INVALID")){
                System.out.println("Wrong Username" + ", " +" Terminating...");
                clientSocket.close();
                System.exit(0);
            }

        } catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
        }

        System.out.println("Username accepted");
    }

    public static void authenticatePassword(String command) {
        String commandPassword = command + "\r\n";

        try {
            // sending password to server.
            outputToServer.writeBytes(commandPassword);
            String responsePassword = getServerResponse();

            if (responsePassword.contains("INVALID")){
                System.out.println("Wrong password" + ", " +" Terminating...");
                clientSocket.close();
                System.exit(0);
            }

        } catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
        }

        System.out.println("Password Accepted");
    }

}
