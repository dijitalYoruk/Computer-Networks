import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class SecureClient {

    // =============================
    // Constants
    // =============================

    // Commands
    public static final String STR_HELLO      = "HELLOxxx";
    public static final String STR_END_ENC    = "ENDENCxx";
    public static final String STR_SECRET     = "SECRETxx";
    public static final String STR_START_ENC  = "STARTENC";
    public static final String STR_AUTH       = "AUTHxxxx";
    public static final String STR_RESPONSE   = "RESPONSE";
    public static final String STR_PUBLIC     = "PUBLICxx";
    public static final String STR_PRIVATE    = "PRIVATEx";
    public static final String STR_LOG_OUT    = "LOGOUTxx";

    // Byte Representation of Commands
    public static final byte[] ARR_BYTE_HELLO    = STR_HELLO.getBytes(StandardCharsets.US_ASCII);
    public static final byte[] ARR_BYTE_RESPONSE = STR_RESPONSE.getBytes(StandardCharsets.US_ASCII);

    // Byte Representation of Certificate Components
    public static final byte[] ARR_BYTE_SIGNATURE = "SIGNATURE".getBytes(StandardCharsets.US_ASCII);
    public static final byte[] ARR_BYTE_NAME = "NAME".getBytes(StandardCharsets.US_ASCII);
    public static final byte[] ARR_BYTE_PK = "PK".getBytes(StandardCharsets.US_ASCII);
    public static final byte[] ARR_BYTE_CA = "CA".getBytes(StandardCharsets.US_ASCII);

    // Sizes of the Byte Representations
    public static final int SIZE_SIGNATURE = ARR_BYTE_SIGNATURE.length;
    public static final int SIZE_NAME = ARR_BYTE_NAME.length;
    public static final int SIZE_PK = ARR_BYTE_PK.length;
    public static final int SIZE_CA = ARR_BYTE_CA.length;

    // =============================
    // Variables
    // =============================

    // Socket Variables
    public static Socket clientSocket;
    public static InputStream inputFromServer;
    public static DataOutputStream outputToServer;
    public static String ipAddress = "127.0.0.1";
    public static String portNumber = "60000";

    // Cryptography Variables
    public static CryptoHelper cryptoHelper;
    public static byte[] publicKey;
    public static byte[] encryptedKey;
    public static int secretKey;
    public static boolean isEncryptionPresent = true;
    public static String authStr = "bilkent cs421";

    public static void main(String[] args) throws InterruptedException {

        // instantiating CryptoHelper
        try {
            cryptoHelper = new CryptoHelper();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        // setting up TCP socket
        //if(args.length > 0 && !args[0].isEmpty()) { ipAddress = args[0]; }
        //if(args.length > 1 && !args[1].isEmpty()) { portNumber = args[1]; }
        if (args.length == 0){
			System.out.println("Missing argument.");
			System.exit(0);
		}
		else
			portNumber = args[0];
		setupSocket();

        // =============================
        // Handshake
        // =============================

        sendCommandWithoutData(STR_HELLO);
        secretKey = cryptoHelper.generateSecret();
        encryptedKey = cryptoHelper.encryptSecretAsymmetric(secretKey, publicKey);;
        sendCommandWithData(STR_SECRET);

        // =============================
        // Authentication
        // =============================

        sendCommandWithoutData(STR_START_ENC);
        sendCommandWithData(STR_AUTH);

        // checking whether authentication was successful
        String isAuthenticated = getServerResponse();
        if (!isAuthenticated.equals("OK")) {
            System.out.println("Could not be Authenticated");
            System.exit(0);
        }

        sendCommandWithoutData(STR_END_ENC);

        // =============================
        // View Public Posts
        // =============================

        sendCommandWithoutData(STR_PUBLIC);
        String posts = getServerResponse();
        System.out.println("==========================");
        System.out.println("=======PUBLIC POSTS=======");
        System.out.println("==========================");
        System.out.println(posts);
        System.out.println();

        // =============================
        // View Private Messages
        // =============================

        sendCommandWithoutData(STR_START_ENC);
        sendCommandWithoutData(STR_PRIVATE);
        String privateMessages = getServerResponse();
        System.out.println("========================");
        System.out.println("====PRIVATE MESSAGES====");
        System.out.println("========================");
        System.out.println(privateMessages + "\n");
        sendCommandWithoutData(STR_END_ENC);

        // =============================
        // Log Out
        // =============================

        sendCommandWithoutData(STR_LOG_OUT);
        System.out.println("Successfully Logged Out");

        // closing the connection
        try {
            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendCommandWithoutData(String command) {
        byte[] type = command.getBytes(StandardCharsets.US_ASCII);
        byte[] size = ByteBuffer.allocate(4).putInt(0).array();
        byte[] commandInByte = new byte[type.length + size.length];
        System.arraycopy(type, 0, commandInByte, 0, type.length);
        System.arraycopy(size, 0, commandInByte, type.length, size.length);

        try {
            outputToServer.write(commandInByte);

            // sending hello command until
            // TCP connection is verified.
            if (command.equals(STR_HELLO)) {
                while (!getServerResponse().equals("true")) {
                    setupSocket();
                    outputToServer.write(commandInByte);
                }
            }
        }
        catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
        }

        if (command.equals(STR_START_ENC)) {
            isEncryptionPresent = true;
        } else if (command.equals(STR_END_ENC)) {
            isEncryptionPresent = false;
        }
    }

    private static void sendCommandWithData(String command) {
        byte[] data = new byte[0];

        // Authentication Command
        if (command.equals(STR_AUTH)) {
            try { data = cryptoHelper.encryptSymmetric(authStr, secretKey); }
            catch (UnsupportedEncodingException e) { e.printStackTrace(); }
        }

        // Secret Command
        else if (command.equals(STR_SECRET)) { data = encryptedKey; }

        // constructing the command
        byte[] type = command.getBytes(StandardCharsets.US_ASCII);
        byte[] size = ByteBuffer.allocate(4).putInt(data.length).array();
        byte[] commandInBytes = new byte[type.length + size.length + data.length];
        int commandSize = type.length + size.length;
        System.arraycopy(type, 0, commandInBytes, 0, type.length);
        System.arraycopy(size, 0, commandInBytes, type.length, size.length);
        System.arraycopy(data, 0, commandInBytes, commandSize, data.length);

        try {
            outputToServer.write(commandInBytes);
        }
        catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
        }
    }

    private static void setupSocket() {
        try {
            clientSocket = new Socket(ipAddress, Integer.parseInt(portNumber));
            inputFromServer = clientSocket.getInputStream();
            outputToServer = new DataOutputStream(clientSocket.getOutputStream());
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getServerResponse() {
        byte[] type = new byte[8];
        byte[] length = new byte[4];
        byte[] data = new byte[0];
        int lengthSize = 0;
        int typeSize = 0;
        int dataSize = 0;

        try {
            typeSize   = inputFromServer.read(type);
            lengthSize = inputFromServer.read(length);
            dataSize = byteArrayToInt(length);
            data = new byte[dataSize];
            dataSize = inputFromServer.read(data);

            // This code is for debugging purposes.
          System.out.println("Type   Size --> " + typeSize   + " Type   --> " + Arrays.toString(type));
            System.out.println("Length Size --> " + lengthSize + " Length --> " + Arrays.toString(length));
            System.out.println("Data   Size --> " + dataSize   + " Cert   --> " + Arrays.toString(data));

        } catch (IOException e) {
            e.printStackTrace();
        }

        // Hello Command, parsing certificate
        if (Arrays.equals(ARR_BYTE_HELLO, type)) {
            HashMap<String, byte[]> certificateComponents = parseCertificate(dataSize, data);
            byte[] signature = certificateComponents.get("SIGNATURE");
            byte[] name = certificateComponents.get("NAME");
            byte[] ca = certificateComponents.get("CA");

            publicKey = certificateComponents.get("PK");
            String strCa = convertByteArrayToString(ca);
            boolean isVerified = cryptoHelper.verifySignature(data, signature, strCa);
            return isVerified ? "true": "false";
        }

        // Response Command, Extracting Respond
        else if (Arrays.equals(ARR_BYTE_RESPONSE, type)) {
            try { return isEncryptionPresent ? cryptoHelper.decryptSymmetric(data, secretKey) : convertByteArrayToString(data); }
            catch (UnsupportedEncodingException e) { e.printStackTrace(); }
        }

        return "";
    }

    public static HashMap<String,byte[]> parseCertificate(int certSize, byte[] cert) {
        ArrayList<Byte> signature = new ArrayList<>();
        ArrayList<Byte> name = new ArrayList<>();
        ArrayList<Byte> ca = new ArrayList<>();
        ArrayList<Byte> pk = new ArrayList<>();

        int i = SIZE_NAME + 1;
        i = parseCertificateHelper(certSize, cert, name, i, SIZE_PK, ARR_BYTE_PK);
        i = parseCertificateHelper(certSize, cert, pk, i, SIZE_CA, ARR_BYTE_CA);
        i = parseCertificateHelper(certSize, cert, ca, i, SIZE_SIGNATURE, ARR_BYTE_SIGNATURE);
        for ( ; i < certSize; i++) { signature.add(cert[i]); }

        byte[] arrSignature = convertListToByteArray(signature);
        byte[] arrName = convertListToByteArray(name);
        byte[] arrCa = convertListToByteArray(ca);
        byte[] arrPk = convertListToByteArray(pk);

        HashMap<String, byte[]> certificateResultMap = new HashMap<>();
        certificateResultMap.put("SIGNATURE", arrSignature);
        certificateResultMap.put("NAME", arrName);
        certificateResultMap.put("CA", arrCa);
        certificateResultMap.put("PK", arrPk);
        return certificateResultMap;
    }

    private static int parseCertificateHelper(int certSize, byte[] cert, ArrayList<Byte> result,
                                              int i, int nextComponentSize, byte[] arrComponent) {
        while (i < certSize) {
            boolean flag = true;

            for (int j = 0; j < nextComponentSize; j++) {
                if (cert[i+j] != arrComponent[j]) { flag = false; break; }
            }

            if (flag) { break; }
            result.add(cert[i++]);
        }

        i += nextComponentSize + 1;
        return i;
    }

    public static byte[] convertListToByteArray(ArrayList<Byte> listByte) {
        int size = listByte.size();
        byte[] arrByte = new byte[size];
        for (int i = 0; i < size; i++) { arrByte[i] = listByte.get(i); }
        return arrByte;
    }

    public static int byteArrayToInt(byte[] b) {
        final ByteBuffer bb = ByteBuffer.wrap(b);
        bb.order(ByteOrder.BIG_ENDIAN);
        return bb.getInt();
    }

    public static String convertByteArrayToString(byte[] arr) {
        StringBuilder strBuilder = new StringBuilder();
        for (byte b : arr) { strBuilder.append((char) (int) b); }
        return strBuilder.toString();
    }

}