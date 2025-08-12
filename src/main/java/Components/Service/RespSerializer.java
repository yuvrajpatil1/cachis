// package Components.Service;

// import java.nio.charset.StandardCharsets;
// import java.util.ArrayList;
// import java.util.List;
// import java.util.logging.Level;
// import java.util.logging.Logger;

// import org.springframework.stereotype.Component;

// @Component
// public class RespSerializer {
//     private static final Logger logger = Logger.getLogger(RespSerializer.class.getName());

//     public String serializeBulkString(String s) {
//         int length = s.length();
//         String respHeader = "$" + length;
//         String respBody = s;
//         return respHeader + "\r\n" + respBody + "\r\n";
//     }

//     public int getParts(char[] dataArr, int i, String[] subArray) {
//         int j = 0;
//         while (i < dataArr.length && j < subArray.length) {
//             if (dataArr[i] == '$') {
//                 // bulk string
//                 i++;
//                 String partLength = "";
//                 while (i < dataArr.length && Character.isDigit(dataArr[i])) {
//                     partLength += dataArr[i];
//                     i++;
//                 }
//                 i += 2;
//                 String part = "";
//                 for (int k = 0; k < Integer.parseInt(partLength); k++) {
//                     part += dataArr[i++];
//                 }
//                 i += 2;
//                 subArray[j++] = part;
//             }
//         }
//         return i;
//     }

//     public List<String[]> deserialize(byte[] command) {
//         try {
//             String data = new String(command, StandardCharsets.UTF_8);
//             char[] dataArr = data.toCharArray();

//             List<String[]> res = new ArrayList<>();

//             int i = 0;
//             while (i < dataArr.length) {
//                 char curr = dataArr[i];
//                 if (curr == '\u0000')
//                     break;
//                 if (curr == '*') {
//                     // array
//                     String arrLen = "";
//                     i++;
//                     while (i < dataArr.length && Character.isDigit(dataArr[i])) {
//                         arrLen += dataArr[i++];
//                     }
//                     i += 2;
//                     if (dataArr[i] == '*') {
//                         for (int t = 0; t < Integer.parseInt(arrLen); i++) {
//                             String nestedLen = "";
//                             i++;
//                             while (i < dataArr.length && Character.isDigit(dataArr[i])) {
//                                 nestedLen += dataArr[i++];
//                             }
//                             i += 2;
//                             String[] subArray = new String[Integer.parseInt(nestedLen)];
//                             i = getParts(dataArr, i, subArray);
//                             res.add(subArray);
//                         }
//                     } else {
//                         // if single command received
//                         String[] subArray = new String[Integer.parseInt(arrLen)];
//                         i = getParts(dataArr, i, subArray);
//                         res.add(subArray);
//                     }
//                 }
//             }
//             return res;
//         } catch (Exception e) {
//             logger.log(Level.SEVERE, e.getMessage());
//         }
//         return new ArrayList<>();
//     }
// }

package Components.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.stereotype.Component;

@Component
public class RespSerializer {
    private static final Logger logger = Logger.getLogger(RespSerializer.class.getName());

    public String serializeBulkString(String s) {
        int length = s.length();
        String respHeader = "$" + length;
        String respBody = s;
        return respHeader + "\r\n" + respBody + "\r\n";
    }

    public int getParts(char[] dataArr, int i, String[] subArray) {
        int j = 0;
        while (i < dataArr.length && j < subArray.length) {
            if (dataArr[i] == '$') {
                // bulk string
                i++;
                String partLength = "";
                while (i < dataArr.length && Character.isDigit(dataArr[i])) {
                    partLength += dataArr[i];
                    i++;
                }

                // Skip \r\n
                if (i < dataArr.length - 1 && dataArr[i] == '\r' && dataArr[i + 1] == '\n') {
                    i += 2;
                }

                if (!partLength.isEmpty()) {
                    String part = "";
                    int length = Integer.parseInt(partLength);
                    for (int k = 0; k < length && i < dataArr.length; k++) {
                        part += dataArr[i++];
                    }

                    // Skip \r\n after content
                    if (i < dataArr.length - 1 && dataArr[i] == '\r' && dataArr[i + 1] == '\n') {
                        i += 2;
                    }

                    subArray[j++] = part;
                }
            }
        }
        return i;
    }

    public List<String[]> deserialize(byte[] command) {
        try {
            String data = new String(command, StandardCharsets.UTF_8);
            char[] dataArr = data.toCharArray();

            List<String[]> res = new ArrayList<>();

            int i = 0;
            while (i < dataArr.length) {
                char curr = dataArr[i];
                if (curr == '\u0000')
                    break;
                if (curr == '*') {
                    // array
                    String arrLen = "";
                    i++;
                    while (i < dataArr.length && Character.isDigit(dataArr[i])) {
                        arrLen += dataArr[i++];
                    }

                    // Skip \r\n
                    if (i < dataArr.length - 1 && dataArr[i] == '\r' && dataArr[i + 1] == '\n') {
                        i += 2;
                    }

                    // Check if arrLen is empty
                    if (arrLen.isEmpty()) {
                        continue;
                    }

                    int arrayLength = Integer.parseInt(arrLen);

                    if (i < dataArr.length && dataArr[i] == '*') {
                        // Handle nested arrays
                        for (int t = 0; t < arrayLength; t++) { // Fixed: increment t, not i
                            if (i >= dataArr.length)
                                break;

                            String nestedLen = "";
                            i++; // skip '*'
                            while (i < dataArr.length && Character.isDigit(dataArr[i])) {
                                nestedLen += dataArr[i++];
                            }

                            // Skip \r\n
                            if (i < dataArr.length - 1 && dataArr[i] == '\r' && dataArr[i + 1] == '\n') {
                                i += 2;
                            }

                            if (!nestedLen.isEmpty()) {
                                String[] subArray = new String[Integer.parseInt(nestedLen)];
                                i = getParts(dataArr, i, subArray);
                                res.add(subArray);
                            }
                        }
                    } else {
                        // Single command received
                        String[] subArray = new String[arrayLength];
                        i = getParts(dataArr, i, subArray);
                        res.add(subArray);
                    }
                } else {
                    i++; // Skip other characters
                }
            }
            return res;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error deserializing: " + e.getMessage(), e);
        }
        return new ArrayList<>();
    }

    public String respInteger(int i) {
        StringBuilder sb = new StringBuilder();
        sb.append(":");
        sb.append(i);
        sb.append("\r\n");
        return sb.toString();
    }

    public String respArray(String[] command) {
        List<String> res = new ArrayList<>();
        int len = command.length;

        res.add("*" + len);
        for (String s : command) {
            int stringLen = s.length();
            s.length();
            res.add("$" + stringLen);
            res.add(s);
        }
        return String.join("\r\n", res) + "\r\n";
    }

    public String respArray(List<String> command) {
        List<String> res = new ArrayList<>();
        int len = command.size();
        res.add("*" + len + "\r\n");
        res.addAll(command);
        return String.join("", res);
    }

    public String[] parseArray(String[] parts) {
        String len = parts[0];
        int length = Integer.parseInt(len);
        String _command[] = new String[length];
        _command[0] = parts[2];
        int idx = 1;

        for (int i = 4; i < parts.length; i += 2) {
            _command[idx++] = parts[i];
        }
        return _command;
    }
}