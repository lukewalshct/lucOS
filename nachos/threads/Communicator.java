package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
	
	//the current message; essentially is a buffer of length 1
	private int _curMessage;
	
	//condition variable that indicates a listener is present
	private ICondition _listenerPresent;
	
	//condition variable that indicates a speaker is present
	private ICondition _speakerPresent;
	
	private ICondition _speakerSlotOpen;
	
	private ICondition _listenerSlotOpen;
	
	private ICondition _messageSet;
	
	private ICondition _messageReceived;
	
	private ICondition _listenerReady;
	
	private ICondition _speakerReady;
	
	//lock for accessing _curMessage
	private Lock _curMessageLock;
	
	private boolean _activeListener;
	
	private boolean _activeSpeaker;
	
	private int _listeners;
	
	private int _speakers;
	
    /**
     * Allocate a new communicator.
     */
    public Communicator() {
    	
    	_curMessageLock = new Lock();
    	
    	_listenerPresent = new Condition(_curMessageLock);
    	
    	_speakerPresent = new Condition(_curMessageLock);
    	
    	_messageSet = new Condition(_curMessageLock);
    	
    	_messageReceived = new Condition(_curMessageLock);
    	
    	_speakerSlotOpen = new Condition(_curMessageLock);
    	
    	_listenerSlotOpen = new Condition(_curMessageLock);
		
		_listenerReady = new Condition(_curMessageLock);
		
		_speakerReady = new Condition(_curMessageLock);
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
    public void speak(int word) {   	
    	
    	//get the lock for the message
    	_curMessageLock.acquire();
    	
		//increment the number of speakers present
		_speakers++;
		
    	//if there's already an "active" speaker, wait until it is finished
    	if(_speakers > 1) _speakerSlotOpen.sleep();    	
		
		_activeSpeaker = true;
    	
		if(_activeListener)
		{			
			//if a listener is present and waiting, let it know there's a speaker
			_speakerPresent.wake();	
		}
		else
		{
			//if there's no listener to take the message, wait for one
			_listenerPresent.sleep();		
		}   		
		
		//let the listener know the speaker is ready
		_speakerReady.wake(); 	
			
		//wait until the listener is ready
		_listenerReady.sleep();		
    	
    	//set the message
    	_curMessage = word;   	
		
		_activeSpeaker = false;		
		
    	//wake up the listener to let it knows the message is ready
    	_messageSet.wake();   			
		
    	//wait until message is received by listener
    	_messageReceived.sleep();    	   						
		
		//if any speakers are present and waiting, let the next one know it can proceed
		_speakerSlotOpen.wake();			
		
		_speakers--;
		
    	//exit critical section
       	_curMessageLock.release();   	   	
    	
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {
    	
    	//get lock for the message
    	_curMessageLock.acquire();
		
		//increment # of listeners present
		_listeners++;
		
    	//if there's already an active listener, wait until it's finished
    	if(_listeners > 1) _listenerSlotOpen.sleep();   	    	
		
		_activeListener = true;		

		if(_activeSpeaker)
		{
			//if a speaker is present and waiting, let it know there's a listener
			_listenerPresent.wake();		
		
			//wait for speaker to be ready
			_speakerReady.sleep();
		}
		else
		{				
			//wait until speaker present
			_speakerPresent.sleep();  				
		}   	

		//let the speaker know the listener is ready
		_listenerReady.wake();					
		
        //wait until message has been set
        _messageSet.sleep();	        
        
    	//once speaker has set message, get the message
    	int msg = _curMessage;   	    			

		_activeListener = false;		
		
    	//let the speaker know the message was received
    	_messageReceived.wake();  				
		
		//if a listener is present and waiting, let it know it can proceed
    	_listenerSlotOpen.wake();			
		
		_listeners--;	
		
    	//exit critical section
    	_curMessageLock.release();   	   	    	
    	
    	//return message
    	return msg;
    }
}
