import java.util.*;
import java.io.*;

public class FileRequest
{
    private String requester;
    private int request_id;
    private String request_description;
    private HashMap<String, ArrayList<String>> uploaded_files;      // ( uploader, [ file_ID ] )

    public FileRequest(int request_id, String requester, String request_description)
    {
        this.request_id = request_id;
        this.requester = requester;
        this.request_description = request_description;
        this.uploaded_files = new HashMap<String, ArrayList<String>>();
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

    public HashMap<String, ArrayList<String>> get_uploaded_files()
    {
        return uploaded_files;
    }

    public void add_file(String uploader, String fileID)
    {
       ArrayList<String> upload_info = null;

        if(uploaded_files.containsKey(uploader))
            upload_info = uploaded_files.get(uploader);             // if request has no uploader against it
        else
            upload_info = new ArrayList<String>();                  // if request has uploader against it

        upload_info.add(fileID);                                    // [ fileID ]
        uploaded_files.put(uploader, upload_info);                  // ( uploader, [ fileID ] )
    }
}