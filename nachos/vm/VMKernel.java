package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.Hashtable;;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	
	//a global inverted page table
	private InvertedPageTable _globalPageTable;
	
    /**
     * Allocate a new VM kernel.
     */
    public VMKernel() {
	super();
    }

    /**
     * Initialize this kernel.
     */
    public void initialize(String[] args) {
	super.initialize(args);
	initializePageTable();
    }
    
    /**
     * Initializes a global inverted page table.
     */
    private void initializePageTable()
    {   	
    	this._globalPageTable = new InvertedPageTable(8, 8); 
    }

    /**
     * Test this kernel.
     */	
    public void selfTest() {
	super.selfTest();
    }

    /**
     * Start running user programs.
     */
    public void run() {
	super.run();
    }
    
    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
	super.terminate();
    }

    // dummy variables to make javac smarter
    private static VMProcess dummy1 = null;

    private static final char dbgVM = 'v';
    
    /** 
     * An inverted page table. The constructor takes as arguments
     * the estimated number of processes the OS should support at once,
     * and the number of estimated pages per process. These are used as
     * the initial capcity of the underlying hash table lookups.
     * 
     * @author luke
     *
     */
    private class InvertedPageTable
    {
    	private Hashtable<Integer, Hashtable<Integer, TranslationEntry>> _pageTable; 	    	
    	
    	private int _numPagesPerProcess;
    	
    	public InvertedPageTable(int numProcesses, int numPagesPerProcess)
    	{
    		this._numPagesPerProcess = numPagesPerProcess;
    		
    		this._pageTable = new Hashtable<Integer, 
    				Hashtable<Integer, TranslationEntry>>(numProcesses);    		    		
    	}
    	
    	/**
    	 * Adds a translation entry for the given process and virtual
    	 * page number. If the hashtable for the process doesn't yet exist,
    	 * create it.
    	 * 
    	 * @return
    	 */
    	public TranslationEntry put(int processID, int virtualPageNumber, 
    			TranslationEntry entry)
    	{
    		Hashtable<Integer, TranslationEntry> processPageTable = 
    				this._pageTable.get(processID);
    		
    		if(processPageTable == null)
    		{
    			processPageTable = new Hashtable<Integer, TranslationEntry>(this._numPagesPerProcess);
    			
    			this._pageTable.put(processID, processPageTable);
    		}
    		
    		processPageTable.put(virtualPageNumber, entry);
    		
    		return entry;
    	}
    	
    	/**
    	 * Retrieves a translation entry based on process id and 
    	 * virtual page number. Returns null if either do not exist.
    	 * @param processID
    	 * @param virtualPageNumber
    	 * @return
    	 */
    	public TranslationEntry get(int processID, int virtualPageNumber)
    	{
    		Hashtable<Integer, TranslationEntry> processPageTable 
    			= this._pageTable.get(processID);
    		
    		if(processPageTable == null) return null;
    		
    		return processPageTable.get(virtualPageNumber);
    	}
    	
    }
}
