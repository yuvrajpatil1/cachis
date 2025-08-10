package Components;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class RespSerializer {
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

    public List<String[]> deserialize(byte[] command) {
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
                i += 2;
                if (dataArr[i] == '*') {
                    for (int t = 0; t < Integer.parseInt(arrLen); i++) {
                        String nestedLen = "";
                        i++;
                        while (i < dataArr.length && Character.isDigit(dataArr[i])) {
                            nestedLen += dataArr[i++];
                        }
                        i += 2;
                        String[] subArray = new String[Integer.parseInt(nestedLen)];
                        i = getParts(dataArr, i, subArray);
                        res.add(subArray);
                    }
                } else {
                    // if single command received
                    String[] subArray = new String[Integer.parseInt(arrLen)];
                    i = getParts(dataArr, i, subArray);
                    res.add(subArray);
                }
            }
        }
        return res;
    }
}
