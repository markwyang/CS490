
import java.rmi.Remote;
import java.rmi.RemoteException;


public interface ChatHandlerInterface extends Remote{;
	public String getName() throws RemoteException;
	public void send(String s) throws RemoteException;
	public void registerClient(ChatHandlerInterface c) throws RemoteException;
	public boolean isAlive(String name) throws RemoteException;
	public ChatHandlerInterface startChat(String name) throws RemoteException;
	public void getList(ChatHandlerInterface target) throws RemoteException;
	public void sendHeartBeat(String name) throws RemoteException;
	public void registerClientExperiment(ChatHandlerInterface client) throws RemoteException;
}
