package Components.Service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

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
                // bulk String
                // $<length>\r\n<data>\r\n
                i++;
                String partLength = "";
                while (i < dataArr.length && Character.isDigit(dataArr[i])) {
                    partLength += dataArr[i];
                    i++;
                }
                i += 2;
                String part = "";
                for (int k = 0; k < Integer.parseInt(partLength); k++) {
                    part += dataArr[i++];
                }
                i += 2;
                subArray[j++] = part;
            }
        }
        return i;
    }

    public List<String[]> deseralize(byte[] command) {
        try {
            String data = new String(command, StandardCharsets.UTF_8);
            char[] dataArr = data.toCharArray();
            List<String[]> res = new ArrayList<>();

            int i = 0;
            while (i < dataArr.length) {

                char curr = dataArr[i];

                if (curr == '\u0000') {
                    break;
                }

                if (curr == '*') {
                    // array
                    String arrLen = "";
                    i++;
                    while (i < dataArr.length && Character.isDigit(dataArr[i])) {
                        arrLen += dataArr[i++];
                    }
                    i += 2;
                    if (dataArr[i] == '*') {
                        // *2
                        // *3\r\n#3set\r\n#3key\r\n#5value
                        // *3\r\n#3set\r\n#3key\r\n#5value
                        for (int t = 0; t < Integer.parseInt(arrLen); t++) {
                            String nestedLen = "";
                            i++;
                            char c = dataArr[i];
                            while (i < dataArr.length && Character.isDigit(dataArr[i])) {

                                nestedLen += dataArr[i++];
                            }
                            i += 2;
                            String[] subArray = new String[Integer.parseInt(nestedLen)];
                            i = getParts(dataArr, i, subArray);
                            res.add(subArray);
                        }
                    } else {
                        // *3\r\n#3set\r\n#3key\r\n#5value
                        String[] subArray = new String[Integer.parseInt(arrLen)];
                        i = getParts(dataArr, i, subArray);
                        res.add(subArray);
                    }
                }
            }
            return res;
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage());
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
            len = s.length();
            res.add("$" + len);
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