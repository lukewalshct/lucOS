package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import java.util.*;

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
	
	private static List<MemNode> freeMemory;
	
	//a lock protecting access to free memory
	private static Lock freeMemLock;
	
    /**
     * Allocate a new user kernel.
     */
    public UserKernel() {
	super();
    }

    /**
     * Initialize this kernel. Creates a synchronized console and sets the
     * processor's exception handler.
     */
    public void initialize(String[] args) {
	super.initialize(args);

	console = new SynchConsole(Machine.console());
	
	Machine.processor().setExceptionHandler(new Runnable() {
		public void run() { exceptionHandler(); }
	    });
	
	initializeFreeMemory();
	
    }

    /**
     * Sets up globally accessible linked list of free physical memory
     * for user processes to "claim" when they are initizlized.
     */
    private void initializeFreeMemory()
    {
    	if(freeMemory != null) return;
    	
    	int pageSize = Machine.processor().pageSize;   	
    	
    	byte [] mainMemory = Machine.processor().getMemory();
    
    	freeMemory = new LinkedList<MemNode>();
    	
    	//set up concurrency protections
    	this.freeMemLock = new Lock();   	
    	
    	for(int i = 0; i < mainMemory.length; i += pageSize)
    	{
    		MemNode memNode = new MemNode();
    		
    		memNode.startIndex = i;
    		
    		memNode.endIndex = i + pageSize - 1;
    		
    		freeMemory.add(memNode);
    	}
    }
    
    /**
     * Gets the next free memory page. This info is stored
     * as indices of the main memory in the MemNode class.
     * 
     * @return the next MemNode containing free page of memory indices
     */
    public static MemNode getNextFreeMemPage()
    {
    	MemNode result = null;
    	
    	//enter critical section
    	freeMemLock.acquire();
    	
    	try
    	{    	
    		if(!freeMemory.isEmpty())
    		{
    			result = freeMemory.remove(0);
    		}
    		else
    		{
    			Lib.debug('u', "NO FREE MEMORY");
    		}
    	}
    	finally
    	{
        	//exit critical section
        	freeMemLock.release();
        	
        	return result;
    	}
    }
    
    /**
     * Adds a memory node/page back to the pool of 
     * free memory. Used when a process ends and is
     * return its memory resources to the pool.
     * 
     * @param node
     */
    public static void returnFreeMemPage(MemNode node)
    {
    	//enter critical section
    	freeMemLock.acquire();
    	
    	try
    	{   	    		
    		if(node != null) freeMemory.add(node);   	
    	}
    	finally
    	{
        	//exit critical section
        	freeMemLock.release();       	
        }
    }
    
    /**
     * Test the console device.
     */	
    public void selfTest() {
	super.selfTest();

	System.out.println("Testing the console device. Typed characters");
	System.out.println("will be echoed until q is typed.");

	char c;

	do {
	    c = (char) console.readByte(true);
	    console.writeByte(c);
	}
	while (c != 'q');

	System.out.println("");
    }

    /**
     * Returns the current process.
     *
     * @return	the current process, or <tt>null</tt> if no process is current.
     */
    public static UserProcess currentProcess() {
	if (!(KThread.currentThread() instanceof UThread))
	    return null;
	
	return ((UThread) KThread.currentThread()).process;
    }

    /**
     * The exception handler. This handler is called by the processor whenever
     * a user instruction causes a processor exception.
     *
     * <p>
     * When the exception handler is invoked, interrupts are enabled, and the
     * processor's cause register contains an integer identifying the cause of
     * the exception (see the <tt>exceptionZZZ</tt> constants in the
     * <tt>Processor</tt> class). If the exception involves a bad virtual
     * address (e.g. page fault, TLB miss, read-only, bus error, or address
     * error), the processor's BadVAddr register identifies the virtual address
     * that caused the exception.
     */
    public void exceptionHandler() {
	Lib.assertTrue(KThread.currentThread() instanceof UThread);

	UserProcess process = ((UThread) KThread.currentThread()).process;
	int cause = Machine.processor().readRegister(Processor.regCause);
	process.handleException(cause);
    }

    /**
     * Start running user programs, by creating a process and running a shell
     * program in it. The name of the shell program it must run is returned by
     * <tt>Machine.getShellProgramName()</tt>.
     *
     * @see	nachos.machine.Machine#getShellProgramName
     */
    public void run() {
	super.run();

	UserProcess process = UserProcess.newUserProcess();
	
	String shellProgram = Machine.getShellProgramName();	
	Lib.assertTrue(process.execute(shellProgram, new String[] { }));

	KThread.currentThread().finish();
    }

    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
	super.terminate();
    }

    /** Globally accessible reference to the synchronized console. */
    public static SynchConsole console;

    // dummy variables to make javac smarter
    private static Coff dummy1 = null;
    
    public class MemNode
    {
    	public int startIndex;
    	
    	public int endIndex;
    }
}
