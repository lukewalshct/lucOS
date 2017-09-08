package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import java.util.*;
import nachos.vm.*; //TODO: remove once static method calls removed

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
	
	private List<PageFrame> freeMemory;
	
	//a lock protecting access to free memory
	private nachos.threads.Lock freeMemLock;
	
	
	//pages (pyhiscal page numbers )in use and cannot be 
	//evicted (e.g. page is being loaded, read/written, etc)
	private HashSet<Integer> _pagesInUse;
	
	//protects access to page table and list of pages in use
	protected Lock _pageAccessLock;
	
	
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
	
	this._pagesInUse = new HashSet<Integer>();
	
	this._pageAccessLock = new nachos.threads.Lock();
	
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
    
    	this.freeMemory = new LinkedList<PageFrame>();
    	
    	//set up concurrency protections
    	this.freeMemLock = new Lock();   	
    	
    	for(int i = 0; i < mainMemory.length; i += pageSize)
    	{
    		PageFrame frame = new PageFrame();
    		
    		frame.startIndex = i;
    		
    		frame.endIndex = i + pageSize - 1;
    		
    		this.freeMemory.add(frame);
    	}
    }
    
    /**
     * Gets the next free memory page. This info is stored
     * as indices of the main memory in the PageFrame class.
     * 
     * @return the next PageFrame containing free page of memory indices
     */
    public PageFrame getNextFreeMemPage(int processID)
    {
    	Lib.assertTrue(Machine.interrupt().disabled());    	    	
    	
    	Lib.debug('s', "Process requesting free memory (PID " + processID + ")");
    	
    	PageFrame result = null;    	    	
    	    	
    	try
    	{    	
        	//enter critical section
        	this.freeMemLock.acquire();
        	
    		if(!this.freeMemory.isEmpty())
    		{
    			Lib.debug('s', "Accessing free memory in main mem (PID " + processID + ")");
    			
    			result = this.freeMemory.remove(0);
    			
        		Lib.assertTrue(result != null, "Memory result null (PID " + processID + ")");        		        	
    		}
    	}
    	finally
    	{    		
        	//exit critical section
        	this.freeMemLock.release();        	        	
        	
        	if(result == null)
        	{
        		Lib.debug('s', "Main mem full. Returning null (PID " + processID + ")");
        	}
        	else
        	{
        		Lib.debug('s', "Returning free memory to process (PID " + processID + ")");
        	}        	
        	
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
    public void returnFreeMemPage(PageFrame frame)
    {
    	//enter critical section
    	this.freeMemLock.acquire();
    	
    	try
    	{   	    		
    		if(frame != null) this.freeMemory.add(frame);   	
    	}
    	finally
    	{
        	//exit critical section
        	this.freeMemLock.release();       	
        }
    }
    
    public void setPageNotInUseAndLock(int ppn)
    {    	    
    	Lib.debug('u', "Acquiring page in use lock for ppn " + ppn);
    	
    	this._pageAccessLock.acquire();
    	
    	Lib.debug('u', "Acquiried page in use lock for ppn " + ppn);
    	
    	try
    	{
    		Lib.debug('u', "Setting page not in use (AND LOCK) - PPN: " + ppn);
    		
    		setPageNotInUse(ppn);
    		
    		Lib.debug('u', "Successfuly set page not in use (AND LOCK) - PPN: " + ppn);
    	}
    	finally
    	{
    		Lib.debug('u', "Releasing page in use lock for ppn " + ppn);
    		
    		this._pageAccessLock.release();
    		
    		Lib.debug('u', "Released page in use lock for ppn " + ppn);
    	}    	
    }
        
    /*
     * Sets a physical page as in use and cannot be evicted.
     */
    public void setPageInUse(int ppn)
    {
    	Lib.assertTrue(this._pageAccessLock.isHeldByCurrentThread());
    	
    	Lib.debug('u', "Setting page in use - PPN: " + ppn);

    	this._pagesInUse.add(ppn);	
    	
    	printPagesInUse('u');
    }
    
    /*
     * Sets a physical page to be not in use and OK to be evicted.
     */
    public void setPageNotInUse(int ppn)
    {
    	Lib.assertTrue(this._pageAccessLock.isHeldByCurrentThread());
    	
    	Lib.debug('u', "Setting page not in use - PPN: " + ppn);    	    	
    	
    	this._pagesInUse.remove(ppn);
    	
    	printPagesInUse('u');
    }
    
    /*
     *  Returuns whether physical page is in use and cannot be evicted.
     */
    public boolean pageInUse(int ppn)
    {
    	Lib.assertTrue(this._pageAccessLock.isHeldByCurrentThread());   	    	
    	
    	return this._pagesInUse.contains(ppn);
    }
    
    public void printPagesInUse(char dbgFlag)
    {
    	StringBuilder sb = new StringBuilder("Physical pages marked as in use: ");    	
    	
    	for(Integer i : this._pagesInUse)
    	{    		
    		sb.append(i.toString() + ", ");
    	}
    	
    	Lib.debug(dbgFlag, sb.toString());
    }
    
    /*
     * Returns length of free memory list
     */
    public int freeMemoryAvailable()
    {
    	return this.freeMemory.size();
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
    
    public class PageFrame
    {
    	public int startIndex;
    	
    	public int endIndex;
    }
}
