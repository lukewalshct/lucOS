package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.Hashtable;;
import java.util.LinkedList;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	
	//a global inverted page table
	private static InvertedPageTable _globalPageTable;  
	
	//a global core map with physical page # as index
	private static TranslationEntry[] _globalCoreMap;
	
	private static SwapFileAccess _globalSwapFileAccess;	
	
	static
	{    	
    	//set up global inverted page table
    	_globalPageTable = new InvertedPageTable(8, 8);
    	
    	//set up global core map
    	Processor processor = Machine.processor();
    	
    	_globalCoreMap = new TranslationEntry[processor.getNumPhysPages()];
	}
	
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
    }
    
    /**
     * Initializes swap file access object.
     */
    public static void initializeSwapFileAccess()
    {    	
    	_globalSwapFileAccess = new SwapFileAccess();
    	
    	_globalSwapFileAccess.initialize();    	
    }
    
    public static SwapFileAccess getSwapFileAccess() { return _globalSwapFileAccess; }
    
    /**
     * Loads the page from swap file on the file system and returns
     * the associated translation entry. 
     * @param pid
     * @param vpn
     * @return
     */
    public static TranslationEntry loadPageFromSwap(int pid, int vpn)
    {
    	Lib.assertTrue(Machine.interrupt().disabled());
    	
    	return _globalSwapFileAccess.loadPage(pid, vpn);
    }
    
    
    public static void putTranslation(int processID, TranslationEntry entry)
    {
    	if(entry == null || entry.ppn < 0 || 
    			entry.ppn >= Machine.processor().getMemory().length)
    	{
    		//TODO: better handle bad translation entries
    		
    		return;
    	}
    		
    	//add entry to global inverted page table
    	_globalPageTable.put(processID, entry);
    	
    	//add entry to core map
    	_globalCoreMap[entry.ppn] = entry;
    }
    
    public static TranslationEntry getTranslation(int processID, int virtualPageNumber)
    {
    	return _globalPageTable.get(processID, virtualPageNumber);
    }

    public static MemNode getNextFreeMemPage()
    {
    	//TODO: handle case where no pyhs memory left (evict some other page)
    	
    	return UserKernel.getNextFreeMemPage();
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
    	
    	if(_globalSwapFileAccess != null) _globalSwapFileAccess.terminate();
    	
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
    private static class InvertedPageTable
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
    	public TranslationEntry put(int processID, TranslationEntry entry)
    	{
    		Hashtable<Integer, TranslationEntry> processPageTable = 
    				this._pageTable.get(processID);
    		
    		if(processPageTable == null)
    		{
    			processPageTable = new Hashtable<Integer, TranslationEntry>(this._numPagesPerProcess);
    			
    			this._pageTable.put(processID, processPageTable);
    		}
    		
    		processPageTable.put(entry.vpn, entry);
    		
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
    
    /**
     * A static data structure representing an API for the kernel to communicate
     * with the swap file on disk. All the translations, free page frames,
     * calls to the file system, etc, are encapsulated within this data 
     * structure.
     * @author luke
     *
     */
    private static class SwapFileAccess
    {
    	//swap file to store swapped pages on disk for demand paging
    	private static OpenFile _swapFile;
    	
    	private static final String _swapFileName = "lucos.swp";
    	
    	//keeps track of page frames in the swap file that are no longer
    	//in use and pages from main memory can be "paged out" to
    	private LinkedList<Integer> _freePageFrames;
    	
    	//a lookup of page frames and translation for a given process id
    	//nad virtual page number
    	private Hashtable<Integer, Hashtable<Integer, SwapEntry>> _swapLookup;
    	
    	public void initialize()
    	{
        	//set up swap file
        	FileSystem fileSys = Machine.stubFileSystem();       	        	
        	
        	//if there's already a swapfile, find it and delete it
        	OpenFile existingSwap = fileSys.open(_swapFileName, false);
        	
        	if(existingSwap != null)
        	{
        		existingSwap.close();
        		
        		fileSys.remove(_swapFileName);
        	}
        	
        	_swapFile = fileSys.open(_swapFileName, true); 
        	
        	_freePageFrames = new LinkedList<Integer>();
        	
        	_swapLookup = new Hashtable<Integer, Hashtable<Integer, SwapEntry>>();
    	}
    	    	
    	public TranslationEntry loadPage(int pid, int vpn)
    	{
    		Hashtable<Integer, SwapEntry> processSwapLookup 
				= _swapLookup.get(pid);
		
    		if(processSwapLookup == null) return null;
		
    		SwapEntry entry = processSwapLookup.get(vpn);
    		
    		if(entry == null) return null;
    		
    		load(entry);
    		
    		return entry.translation;
    	}
    	
    	private void load(SwapEntry entry)
    	{
    		//TODO: load page into main memory
    		
    	}
    	
    	public void terminate()
    	{
        	//delete swap file from disk
        	FileSystem fileSys = Machine.stubFileSystem();
        	
        	if(_swapFile != null) _swapFile.close();
        	
        	fileSys.remove(_swapFileName);
    	}
    	
    	private class SwapEntry
    	{
    		public int pageFrameIndex;
    		
    		public TranslationEntry translation;   		    		 
    	}
    }
}
