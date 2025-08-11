package Components.Service;

public class ResponseDto {
    public String response = null;
    public byte[] data = null;

    public ResponseDto(String response) {
        this.response = response;
    }

    public ResponseDto(String response, byte[] data) {
        this.response = response;
        this.data = data;
    }
}
