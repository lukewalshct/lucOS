package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.util.Arrays;
import java.util.Hashtable;;
import java.util.LinkedList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	
	//a global inverted page table
	private InvertedPageTable _globalPageTable;  
	
	//a global core map with physical page # as index
	private CoreMapEntry[] _globalCoreMap;
	
	private SwapFileAccess _globalSwapFileAccess;	
	
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
       	//set up global inverted page table
    	this._globalPageTable = new InvertedPageTable(8, 8);
    	
    	//set up global core map
    	Processor processor = Machine.processor();
    	
    	this._globalCoreMap = new CoreMapEntry[processor.getNumPhysPages()];
    	
    	super.initialize(args);	
    }
    
    /**
     * Initializes swap file access object.
     */
    public void initializeSwapFileAccess()
    {    	
    	this._globalSwapFileAccess = new SwapFileAccess();
    	
    	this._globalSwapFileAccess.initialize();    	
    }
    
    public SwapFileAccess getSwapFileAccess() { return this._globalSwapFileAccess; }
    
    /**
     * Loads the page from swap file on the file system and returns
     * the associated translation entry. 
     * @param pid
     * @param vpn
     * @return
     */
    public TranslationEntry loadPageFromSwap(int pid, int vpn)
    {
    	Lib.assertTrue(Machine.interrupt().disabled());
    	
    	TranslationEntry entry = this._globalSwapFileAccess.loadPage(pid, vpn);
    	
    	if(entry != null) entry.valid = true;
    	
    	return entry;
    }
    
    
    public void putTranslation(int processID, TranslationEntry entry)
    {
    	if(entry == null || entry.ppn < 0 || 
    			entry.ppn >= Machine.processor().getMemory().length)
    	{
    		//TODO: better handle bad translation entries
    		
    		return;
    	}
    		
    	//add entry to global inverted page table
    	this._globalPageTable.put(processID, entry);
    	
    	//add entry to core map
    	this._globalCoreMap[entry.ppn] = new CoreMapEntry(processID, entry);
    }
    
    public TranslationEntry getTranslation(int processID, int virtualPageNumber)
    {
    	return getTranslation(processID, virtualPageNumber, false);    	
    }    
    
    public TranslationEntry getTranslation(int processID, 
    		int virtualPageNumber, boolean nonEvictable)
    {
    	TranslationEntry entry;
    	
    	if(nonEvictable)
    	{
    		this._pageEvictionLock.acquire();    	
    		
    		try
    		{
    			entry = this._globalPageTable.get(processID, virtualPageNumber);
    			
    			if(entry != null) entry.used = true;
    		}
    		finally
    		{
    			this._pageEvictionLock.release();
    		}
    	}
    	else
    	{
    		entry = this._globalPageTable.get(processID, virtualPageNumber);
    	}   	
    	
    	return entry;    	
    }

    /*
     * Called when main memory is full but a process needs a 
     * free page of memory. Frees up a page in main memory by
     * writing the page to the swap file (if applicable) and 
     * returns a MemNode that represents the new empty slot in 
     * main memory.
     */    
    @Override
    protected MemNode freeUpMemory(int processID)
    {
    	int freePageNum = evictPage(processID);
    	
    	if(freePageNum < 0) return null;
    	
    	int pageSize = Machine.processor().pageSize;   	
    	
		MemNode memNode = new MemNode();
		
		memNode.startIndex = freePageNum * pageSize;
		
		memNode.endIndex = memNode.startIndex + pageSize - 1;
    	
    	return memNode;
    }
    
    /*
     * Evicts a page from main memory.
     */
    private int evictPage(int processID)
    {
    	CoreMapEntry mapEntry = null;
    	
    	int physPageNum = -1;
    			
    	while(mapEntry == null || mapEntry.entry == null || mapEntry.entry.used)
    	{
    		//Currently chooses page at random. TODO: implement nth chance algorithm
    		physPageNum = ThreadLocalRandom.current().nextInt(0, _globalCoreMap.length);
    	
    		mapEntry = this._globalCoreMap[physPageNum];
    	}
    	
    	if(mapEntry == null) return -1;
    	
    	//write old page to the swap file
    	this._globalSwapFileAccess.writePage(processID, mapEntry.entry);
    	
    	//remove references to the page from core map and global inverted page table
    	this._globalCoreMap[physPageNum] = null;
    	
    	this._globalPageTable.remove(mapEntry.processID, mapEntry.entry.vpn);
    	
    	if(mapEntry.processID == processID) invalidateTLBEntry(mapEntry.entry.vpn);
    	
    	return physPageNum;
    }
    
    /**
     * Invalidates TLB entry with the given vpn if it's in the TLB.
     */
    private void invalidateTLBEntry(int vpn)
    {
    	Processor processor = Machine.processor();
    	
    	for(int i = 0; i < processor.getTLBSize(); i++)
    	{    		
    		TranslationEntry entry = processor.readTLBEntry(i);
    		    		
    		if(entry != null && entry.vpn == vpn)
    		{
    			entry.valid = false;
    			
    			processor.writeTLBEntry(i, entry);    		
    		}   		    		
    	}
    }
    
    /*
     * Deallocates memory for a process and cleans up
     * tranlsations, TLB, etc.
     */
    protected void deallocateProcessMemory(int processID)
    {    
    	Lib.assertTrue(Machine.interrupt().disabled());
    	
    	//free up any pages in swap file
    	
    	//remove translations from core map
    	TranslationEntry[] entries = this._globalPageTable.getAll(processID);
    	
    	if(entries == null) return;
    	
    	for(int i = 0; i < entries.length; i++)
    	{
    		if(entries[i] != null)
    		{
    			this._globalCoreMap[entries[i].ppn] = null;
    		}
    	}
    	
    	//remove translations from global page table
    	this._globalPageTable.removeAll(processID);
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
    	
    	if(this._globalSwapFileAccess != null) this._globalSwapFileAccess.terminate();
    	
    	super.terminate();
    }

    // dummy variables to make javac smarter
    private static VMProcess dummy1 = null;

    private static final char dbgVM = 'v';
    
    private class CoreMapEntry
    {
    	public int processID;
    	
    	public TranslationEntry entry;
    	
    	public CoreMapEntry(int pid, TranslationEntry entry)
    	{
    		this.processID = pid;
    		
    		this.entry = entry;
    	}
    }
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
    	
    	/*
    	 * Gets all translation entries for a process.
    	 */
    	public TranslationEntry[] getAll(int processID)
    	{
    		Hashtable<Integer, TranslationEntry> processPageTable
    			= this._pageTable.get(processID);
    		
    		if(processPageTable == null || processPageTable.values() == null) return null;
    		
    		Object[] values = processPageTable.values().toArray();
    		    		
    		return Arrays.copyOf(values, values.length, TranslationEntry[].class);
    	}
    	
    	public TranslationEntry remove(int processID, int virtualPageNumber)
    	{
    		Hashtable<Integer, TranslationEntry> processPageTable 
    			= this._pageTable.get(processID);
		
    		if(processPageTable == null) return null;
		
    		return processPageTable.remove(virtualPageNumber);	
    	}
    	
    	/*
    	 * Removes all translations for a process.
    	 */
    	public void removeAll(int processID)
    	{
    		this._pageTable.remove(processID);
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
    private class SwapFileAccess
    {
    	//swap file to store swapped pages on disk for demand paging
    	private OpenFile _swapFile;
    	
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
        	
        	this._swapFile = fileSys.open(_swapFileName, true); 
        	
        	this._freePageFrames = new LinkedList<Integer>();
        	
        	this._swapLookup = new Hashtable<Integer, Hashtable<Integer, SwapEntry>>();
    	}
    	 
    	/**
    	 * Looks up the page in the swap file for the given process id and
    	 * virtual page number. Loads the page into main memory, and returns
    	 * the translation entry for the page.
    	 * 
    	 * @param pid
    	 * @param vpn
    	 * @return
    	 */
    	public TranslationEntry loadPage(int pid, int vpn)
    	{
    		Lib.debug('s', "Attempting to load from swap (PID " + pid + " VPN " + vpn + ")");
    		
    		Hashtable<Integer, SwapEntry> processSwapLookup 
				= this._swapLookup.get(pid);
		
    		if(processSwapLookup == null)
    		{
    			Lib.debug('s', "Swap load failed (PID " + pid + " VPN " + vpn + ")");
    			
    			return null;
    		}
		
    		SwapEntry entry = processSwapLookup.get(vpn);
    		
    		if(entry == null)
    		{
    			Lib.debug('s', "Swap load failed (PID " + pid + " VPN " + vpn + ")");
    			
    			return null;
    		}
    		
    		load(entry);
    		
    		return entry.translation;
    	}
    	
    	/**
    	 * Writes page from main memory to swap file.
    	 * @return
    	 */
    	public boolean writePage(int pid, TranslationEntry entry)
    	{
    		if(entry == null) return false;
    		    
    		Lib.debug('s', "Attempting to write to swap (PID " + pid + " VPN " + entry.vpn + ")");
    		
    		//get the swap lookup for the process, create if doesn't exist
    		Hashtable<Integer, SwapEntry> processSwapLookup 
				= this._swapLookup.get(pid);
    		
    		if(processSwapLookup == null)
    		{
    			processSwapLookup = new Hashtable<Integer, SwapEntry>();    			
    			
    			this._swapLookup.put(pid, processSwapLookup);
    		}
    		
    		//try to get the existing page frame index    		    	
    		SwapEntry swapEntry = processSwapLookup.get(pid);
    		
    		if(swapEntry == null) swapEntry = new SwapEntry(-1, entry);
    		
    		boolean success = writeToSwap(swapEntry);
    		
    		Lib.debug('s', "Write to swap " + (success ? "" : "un") + 
    				"successful (PID " + pid + " VPN " + entry.vpn + ")");
    		
    		return success;
    	}
    	
    	private boolean writeToSwap(SwapEntry swapEntry)
    	{
    		if(swapEntry == null || swapEntry.translation == null) return false;
    		
    		//get main memory from the processor
    		byte[] memory = Machine.processor().getMemory();
    		
    		int ppn = swapEntry.translation.ppn;
    		
    		//validate physical page number
    		if (ppn < 0 || ppn >= memory.length)
    		    return false;    		 
    		
    		//get page to be written from main memory
    		byte[] pageToWrite = new byte[Machine.processor().pageSize];
    		
    		System.arraycopy(memory, ppn, pageToWrite, 0, Machine.processor().pageSize);
    		
    		int pageFrameIndex = swapEntry.pageFrameIndex >= 0 ? 
    				swapEntry.pageFrameIndex : Math.max(_swapFile.length(), 0);
    				
    		//write page to swap
    		int bytesWritten = _swapFile.write(pageFrameIndex, 
    				pageToWrite, 0, pageToWrite.length);
    		
    		return bytesWritten == Machine.processor().pageSize;
    	}
    	
    	/**
    	 * Loads page retrieved from swap file into memory.
    	 * @param entry
    	 */
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
    		
    		public SwapEntry(int pageFrameIndex, TranslationEntry entry)
    		{
    			this.pageFrameIndex = pageFrameIndex;
    			
    			this.translation = entry;
    		}
    	}
    }
}
