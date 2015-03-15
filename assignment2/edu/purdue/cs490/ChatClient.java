package edu.purdue.cs490;

/*
TESTS TO DO
1] SERVER CLOSES
2] OTHER CLIENT CLOSES
3] SERVER TERMINATES CLIENT
*/
import java.net.SocketException;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Iterator;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.ServerSocket;
import java.util.Hashtable;
import java.io.InterruptedIOException;

	/**************************************************************************************************
	*									SPECIFICATIONS FOR SERVER SIDE								*
	**************************************************************************************************/
	/**
	1) When it connects with the server, it will send 2 Strings:
		1) "R"[username][spaces until 13 characters][local ipv4 address][space][port #], e.g.: RCharlie      127.0.0.1 42691
		2) and "get". It will expect the server to send a list of users with the following format for each single user
			[length of name][username][local ipv4 address][space][port #], e.g.: 07Charlie127.0.0.1 42691.
			It will keep reading users and waiting until it sees "\\0", so be sure to println that after your for loop is done
		3) If registration is valid, it expects a response. It can be any response, but if it's "U", it will disconnect.
	2) It will continually send "<3" every heartbeat_rate. It's its heartbeat.
	3) It can send "get" anytime after it connects with the server for any number of times.
	4) Any number of clients can try to connect to the server
	5) Do NOT allow multiple clients with the same name. Right during the connection, the client will listen for a message. If
		the message is "U", it will tell the user to change his/her username. (aka send "U" back if name is a repeat). The client
		will also send a "get" so the user can know which usernames are still available
	*/

public class ChatClient extends Process implements Runnable
{
	/**************************************************************************************************
	*											FIELDS												*
	**************************************************************************************************/
	private ChannelInterface channel;
	private static long heartbeat_rate = 5000;
	private String host;
	private String name;//name of the Client
	private String id;
	private int serverPort;//port of the server it's going to connect to
	private int clientPort;//port of the client's ServerSocket for chatting with other clients
	private String ip;//ip of the client
	private Socket socket;//socket for connecting purposes
	private ServerSocket serverSocket;//for connecting to other users directly???
	private ObjectOutputStream heart;//printer to server
	private ObjectInputStream heartListener;//reader to server
	private PrintWriter printer;//printer to client
	private BufferedReader reader;//reader to client
	private Socket currentChatSocket;//the current Socket you're chatting in right now
	private boolean inChat;//once someone gets a message, they are forced in chat
	private long heartbeat = 0;
	private ClientObject myClientObject;//object representing this specific client for server purposes
	private ConcurrentHashMap<String,ClientObject> listOfUsers = new ConcurrentHashMap<String,ClientObject>();//hashmap of users for connecting to others
	private String[] commands = {"\\hey","\\switch","\\list","\\everybody","\\help","?"};//list of available commands
	private ClientObject currentInterlocuter;

	/**************************************************************************************************
	*											MAIN METHOD											*
	**************************************************************************************************/
	public static void main(String[] args)
	{
		try
		{
			ChatClient myChatClient = new ChatClient("-1",-1,"-1");//because of the fucking superclass's constructor requirements			
			myChatClient.register();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		System.exit(0);
	}
	/************************************************************************************************
	*											INITIALIZATION									*
	*************************************************************************************************/
	public void register() throws Exception
	{
		System.out.print("Hostname of the server you want to connect to:");
		Scanner console = new Scanner(System.in);
		host = console.next();
		//check validity
		System.out.print("Port of the server to connect to:");
		//check validity
		serverPort = console.nextInt();
		console.nextLine();
		System.out.print("Enter your name:");
		name = console.nextLine();
		channel = new ChannelInterface();		
		channel.initServer(host,serverPort);
		new Thread(this).start();
		while(clientPort == -1){}//waits for serverSocket to be initialized. Once it's initialized, clientPort will have a value
		myClientObject = new ClientObject(name, InetAddress.getLocalHost().getHostAddress(), clientPort);
		channel.toServer("reg");
		channel.toServer(myClientObject);
		String verification = (String) channel.fromServer();
		while(verification.equals("U"))
		{
			System.out.println("Registration failed because you have the same name as another user");
			System.out.println("Enter your username again!");
			myClientObject.setName(console.nextLine());
			channel.closeServer();
			channel.initServer(host,serverPort);
			channel.toServer("reg");
			channel.toServer(myClientObject);
			verification = (String)channel.fromServer();
		}
		id = verification;
		System.out.println("Verified!");
		displayCommands();
		this.heartbeat();
	}
	/**********************************************************************************************
	*											HEARTBEAT									*
	***********************************************************************************************/
	public void heartbeat()
	{
		while(true)
		{
			if(System.currentTimeMillis()-heartbeat > heartbeat_rate)
			{
				heartbeat = System.currentTimeMillis();
				channel.toServer("<3");
			}
		}
	}
	/**************************************************************************************************
	*										GET AND DISPLAY											*
	**************************************************************************************************/
	public void getAndDisplay()
	{
		try
		{		
			String user;
			channel.toServer("get");
			System.out.println("Current people online:");
			//needs InvalidProtoclException
			listOfUsers = (ConcurrentHashMap<String, ClientObject>)channel.fromServer();			
			//iterate through the hashmap
			Iterator availableUsers = listOfUsers.entrySet().iterator();
			int counter = 1;
			while(availableUsers.hasNext())
			{
				Map.Entry pair = (Map.Entry)availableUsers.next();
				System.out.printf("%d. %s\t",counter++,pair.getKey());
			}
			System.out.println("\n================================================================================");
		}		
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	/**************************************************************************************************
	*								PRINTING AVAILABLE COMMANDS										*
	**************************************************************************************************/
	public static void displayCommands()
	{
		System.out.println("Available commands:\n\\hey [user] = initiates chat session with [user]");
		System.out.println("\\switch [user] = switches to another user to send messages to");
		System.out.println("\\list = shows you the list of all available users on this server");
		System.out.println("\\everybody [message] = allow everyone to hear what you want to say");
		System.out.println("\\help = shows available commands (same as \'?\')\n? = shows available commands (same as \'help\')");
	}

	/**************************************************************************************************
	*								THREAD THAT THE USER INTERACTS WITH								*
	**************************************************************************************************/
	/*Checks if user inputted a command, then extracts it*/
	public String isCommand(String input)
	{
		for(int i = 0; i < commands.length; i++)
		{
			if(input.indexOf(commands[i]) == 0)//checks if it is a command
				return commands[i];//if it is, return it
		}
		return "";//else return nothing
	}
	/*Based on commands, does special actions*/
	public void executeCommand(String command, String message)
	{
		if(command.equals(commands[0]))
		{//command == hey; initialize new socket and add to socketList
			currentInterlocuter = listOfUsers.get(message);
			if(currentInterlocuter != null)
			{
				ClientObject temp = channel.initClient(currentInterlocuter);
				if(temp != null)//initClient returns null if something goes wrong
					currentInterlocuter = temp;
				else
				{
					System.out.printf("That user disconnected!\n");//insert debugging stuff here					
				}
				System.out.printf("Chatting with %s\n",message);				
			}
			else
			{
				System.out.printf("%s is not online. Please check your spelling!\n",message);
			}
		}
		if(command.equals(commands[1]))
		{//switch; switch socket to user
			currentInterlocuter = listOfUsers.get(message);
			if(currentInterlocuter == null)
			{
				System.out.printf("%s is not online. Please check your spelling!\n",message);
			}
		}
		if(command.equals(commands[2]))
		{//list
			getAndDisplay();
		}
		if(command.equals(commands[3]))
		{//everybody
			System.out.println("RB");
		}
		if(command.equals(commands[4]) || command.equals(commands[5]))
		{//help and ?
			displayCommands();
		}
		if(command.equals(""))
		{//normal typing
			if(currentInterlocuter != null)
				channel.whisper(currentInterlocuter,message);
			else
				System.out.println("Unrecognized command! Enter \'?\' or \'\\help\' for a list of commands.");
		}

	}
	public void run()
	{
		String message;//the message string we're going to be dealing with mainly
		String command;//if it is a valid command, command!
		Scanner console = new Scanner(System.in);
		while(true)
		{
			message = console.nextLine();
			command = isCommand(message);
			if(command.length() != 0)
			{
				if(message.length() > command.length())
				{
					message = message.substring(command.length()+1,message.length());//keeps rest of message				
				}
				else
					message = "";				
			}
			executeCommand(command,message);
		}
	}
	/**************************************************************************************************
	*								GRABBING CHAT FROM OTHER CLIENTS								*
	**************************************************************************************************/
	private ChatServer chat;

	public ChatClient(String tempIP, int tempPort, String tempID)
	{
		super(tempIP, tempPort, tempID);
		try
		{
			new Thread(new ChatServer()).start();//for waiting for other clients to connect and receiving messages
			chat = new ChatServer();
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}

	public class ChatServer implements Runnable//ChatClient creates a ChatServer, which means two threads are started in main
	{
		public ChatServer() throws IOException
		{
			serverSocket = new ServerSocket(0);//initializes serverSocket
			serverSocket.setReuseAddress(true);
			serverSocket.setSoTimeout(100);//sets a timeout for serverSocket.accept() so when WE initialize contact, we can continue on this thread
			clientPort = serverSocket.getLocalPort();//clientPort is set up
		}
		public void run()
		{
			String user = "";//user will be the name displayed when chatting e.g. Charlie: hi
			String message = null;
			while(channel == null){};
			while(true)
			{
				try
				{
					channel.initInvitation(serverSocket);
				}
				catch(SocketTimeoutException e)
				{
					channel.fromClient();
				}
				catch(Exception e)
				{
					e.printStackTrace();
				}
			}
		}
	}
}
