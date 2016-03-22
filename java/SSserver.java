import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.io.DataInputStream;  
import java.io.DataOutputStream;  
import java.net.ServerSocket;  
import java.net.Socket; 
import java.net.SocketTimeoutException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SSserver {

    private class SServerInstance implements Runnable {

        final private int HEAD_BUFF_LEN = 64;

        final private int ADDR_TYPE_HOST = 0x03;

        final private int TIMEOUT = 3000;

        private Socket mLocal;
        private String mRemoteAddr;
        private int mRemotePort;

        public SServerInstance(Socket l)
        {
            mLocal = l;
        }

        private void parseHead(DataInputStream in) throws Exception
        {
            int addrtype = in.read();
            int size = 0;
            byte buf[] = new byte[HEAD_BUFF_LEN];

            //get addr
            if (addrtype == ADDR_TYPE_HOST) {
                size = in.read();
                size = in.read(buf, 0, size);
            } else {
                //do not support other addrtype now.
                throw new Exception("Unsupport addr type!");
            }
            mRemoteAddr = new String(buf, 0, size);

            //get port
            in.read(buf, 0, 2);
            ByteBuffer bb = ByteBuffer.allocate(2);
            bb.order(ByteOrder.BIG_ENDIAN);
            bb.put(buf[0]);
            bb.put(buf[1]);
            mRemotePort = bb.getShort(0);

            System.out.println("Addr type: " + addrtype + " addr: " + mRemoteAddr + ":" + mRemotePort);
        }

        public void run()
        {
            int addrtype;
            int size;

            try(DataInputStream lin = new DataInputStream(mLocal.getInputStream());
                    DataOutputStream lout = new DataOutputStream(mLocal.getOutputStream()))
            {

                parseHead(lin);

                try(Socket remote = new Socket())
                {
                    remote.setTcpNoDelay(true);
                    remote.connect(new InetSocketAddress(mRemoteAddr, mRemotePort), TIMEOUT);

                    try(DataInputStream rin = new DataInputStream(remote.getInputStream());
                            DataOutputStream rout = new DataOutputStream(remote.getOutputStream());)
                    {
                        Thread t1 = new Thread(new handleStream(lin, rout));
                        Thread t2 = new Thread(new handleStream(rin, lout));
                        t1.start();
                        t2.start();
                        t1.join();
                        t2.join();
                    }catch(Exception e){
                        throw e;
                    }
                }catch(Exception e){
                    throw e;
                }

            }catch (Exception e) {
                System.err.println("Exception: " + e.getMessage() + "! Target address: " + mRemoteAddr);
            }finally{
                try {
                    mLocal.close();
                }catch(Exception e) {
                    System.err.println(e.getMessage());
                }
            }
        }
    }
    // FIXME: this is ugly...
    private class handleStream implements Runnable {

        final private int BUFF_LEN = 4096;

        private DataInputStream mInput;
        private DataOutputStream mOutput;

        public handleStream(DataInputStream i, DataOutputStream o){
            mInput = i;
            mOutput = o;
        }

        public void run()
        {
            byte buf[] = new byte[BUFF_LEN];
            int r_size, w_size, total_r_size = 0;
            while (true) {
                try{
                    r_size = mInput.read(buf, 0, BUFF_LEN);
                    total_r_size += r_size;
                    if (r_size <= 0) 
                        break;
                    mOutput.write(buf, 0, r_size);
                }catch(SocketTimeoutException e){
                    // ignore
                    break;
                }catch(Exception e){
                    e.printStackTrace();
                    break;
                }
                w_size = mOutput.size();
                if (total_r_size != w_size) {
                    System.err.println("Total read size: " + total_r_size + " != Total write size: " + w_size);
                }
            }
        }
    }

    public SSserver()
    {
    }
    
    public void handle()
    {
        int port = 2048;
        int timeout = 30000;
        Executor service = Executors.newCachedThreadPool();
        try(ServerSocket server = new ServerSocket(port)) {
            server.setReuseAddress(true);
            while (true) {
                Socket local = server.accept();
                local.setTcpNoDelay(true);
                local.setSoTimeout(timeout);
                service.execute(new SServerInstance(local));
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public static void main(String argv[])
    {
        System.out.println("Shadowsocks-Java v0.02");
        new SSserver().handle();;
    }
}
