import java.io.*;
import java.util.*;

public class ClientMessages
{
    private String fileID;
    private String requester;
    private int req_id;
    private String uploader;

    ClientMessages(String fileID, String requester, int req_id, String uploader)
    {
        this.fileID = fileID;
        this.req_id = req_id;
        this.requester = requester;
        this.uploader = uploader;
    }

    public String get_fileID()
    {
        return fileID;
    }

    public String get_requester()
    {
        return requester;
    }

    public String get_uploader()
    {
        return uploader;
    }

    public int get_req_id()
    {
        return req_id;
    }
}