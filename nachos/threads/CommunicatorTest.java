package nachos.threads;

import java.util.Arrays;

/*
 * A class that tests Communicator.java by simulating commuications between
 * speakers and listeners and ensuring that all communications are sent/
 * received successfully.
 */
public class CommunicatorTest {

	private int[] _messagesToSend; // we'll check these are all equal to 0 after communications sent
	
	private int[] _messagesReceived; //we'll check these are all equal to 1 after communications sent
	
	private final int NUM_SPEAKERS = 3;
	
	private final int NUM_MESSAGES = 5;
	
	public void Test()
	{
		//create messages to send (will all be equal to numSpeakers) 
		_messagesToSend = new int[NUM_MESSAGES];
		
		Arrays.fill(_messagesToSend,  NUM_SPEAKERS);
		
		_messagesReceived = new int [NUM_MESSAGES];
		
		KThread[] speakers = getSpeakers(3);
		
		KThread[] listeners = getListeners(3); 
	}
	
	private KThread[] getSpeakers(int quantity)
	{
		return null;
	}
	
	private KThread[] getListeners(int quantity)
	{
		return null;
	}
	
	
}
