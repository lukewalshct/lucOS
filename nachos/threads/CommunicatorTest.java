package nachos.threads;

import java.util.Arrays;

/*
 * A class that tests Communicator.java by simulating commuications between
 * speakers and listeners and ensuring that all communications are sent/
 * received successfully.
 */
public class CommunicatorTest extends KernelTestBase {

	private int[] _messagesToSend; // we'll check these are all equal to 0 after communications sent
	
	private int[] _messagesReceived; //we'll check these are all equal to 1 after communications sent
	
	private int _numMessages; //represents num remaining messages - num remaining listeners
	
	private Lock _numMsgLock;
	
	private int _numSpeakers;
	
	private int _numListeners;
	
	private final int NUM_MSG_PER_SPEAKER = 3;
	
	public void Test()
	{
		System.out.println("\nCOMMUNICATOR TEST BEGINNING\n");
		
		Communicator communicator = new Communicator();
		
		_numMsgLock = new Lock();
		
		//create messages to send (will all be equal to numSpeakers) 
		_messagesToSend = new int[NUM_MSG_PER_SPEAKER];
		
		Arrays.fill(_messagesToSend,  _numSpeakers);
		
		_messagesReceived = new int [NUM_MSG_PER_SPEAKER];
		
		KThread[] speakers = getSpeakers(3, communicator);
		
		KThread[] listeners = getListeners(5, communicator); 
		
		runThreads(listeners);
		
		runThreads(speakers);
		
		try{
			joinThreads(listeners);
		}catch(Exception ex) {}
		
		//TODO: add verification/accounting for all messages sent/received
		
		System.out.println("\nCOMMUNICATOR TEST ENDING\n");
	}
	
	private KThread[] getSpeakers(int quantity, Communicator c)
	{
		KThread[] speakers = new KThread[quantity];
		
		for(int i = 0; i < quantity; i++)
		{
			speakers[i] = new KThread(new Speaker(c, NUM_MSG_PER_SPEAKER));
			
			_numSpeakers++;
		}
		
		return speakers;
	}
	
	private KThread[] getListeners(int quantity, Communicator c)
	{
		KThread[] listeners = new KThread[quantity];
		
		for(int i = 0; i < quantity; i++)
		{
			listeners[i] = new KThread(new Listener(c));
			
			_numListeners++;
		}
		
		return listeners;
	}
	
	private class Speaker implements Runnable {
		
		private int _numMessages;
		
		private Communicator _communicator;
		
		public Speaker(Communicator communicator, int numMessages)
		{
			_communicator = communicator;
			
			_numMessages = numMessages;				
		}
		
		public void run()
		{
			speakMessages();
		}
		
		private void speakMessages()
		{
			for(int i = 0; i < _numMessages; i++)
			{
				_numMsgLock.acquire();
				
				//increment number of messages to be removed
				_numMessages++;
				
				//if it's the last item, the speaker will "retire"; decrement num speakers
				if(i+1 == _numMessages) _numSpeakers--;
				
				_numMsgLock.release();
				
				//send the message
				_communicator.speak(i);
				
				//account for the message being sent
				_messagesToSend[i]--;
				
				KThread.yield();
			}
		}
	}
	
	private class Listener implements Runnable {
		
		private Communicator _communicator;
		
		public Listener(Communicator communicator)
		{
			_communicator = communicator;
		}
		
		public void run()
		{
			listen();
		}
		
		private void listen()
		{
			while(continueListening())
			{
				//get message
				int msg = _communicator.listen();
				
				//account for the message
				_messagesReceived[msg]++;
				
				KThread.yield();
			}
		}
		
	}
	
	private boolean continueListening()
	{
		_numMsgLock.acquire();
		
		boolean shouldContinue = (_numSpeakers >= _numListeners) ||
				 (_numSpeakers == 0 && _numMessages >= _numListeners);
		 
		 if(shouldContinue)
		 {
			 _numMessages--;
		 }
		 else
		 {
			 _numListeners--;
		 }
		 
		 _numMsgLock.release();
		
		return shouldContinue;
	}	
	
}
