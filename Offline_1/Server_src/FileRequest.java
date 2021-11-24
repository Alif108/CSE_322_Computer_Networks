import java.util.*;
import java.io.*;

public class FileRequest
{
    private String requester;
    private int request_id;
    private String request_description;
    private HashMap<String, String> uploaded_files;      // ( uploader, file_ID )
    private boolean issued;

    public FileRequest(int request_id, String requester, String request_description)
    {
        this.request_id = request_id;
        this.requester = requester;
        this.request_description = request_description;
        this.uploaded_files = new HashMap<String, String>();
        this.issued = false;
    }

    public int get_request_id()
    {
        return request_id;
    }

    public String get_requester()
    {
        return requester;
    }

    public String get_req_desc()
    {
        return request_description;
    }

    public HashMap<String, String> get_uploaded_files()
    {
        return uploaded_files;
    }

    public void add_file(String uploader, String fileID)
    {
        uploaded_files.put(uploader, fileID);
    }

    public boolean isIssued()
    {
        return issued;
    }
}