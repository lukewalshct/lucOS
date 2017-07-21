package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.userprog.UserKernel.MemNode;

import java.io.EOFException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */
public class UserProcess {
	
	private final int MAX_FILE_NAME_BYTES = 256;
	
	private final int MAX_OPEN_FILES = 16;
	
	private int numOpenFiles;
	
	private OpenFile[] openFiles;
	
	//represents the phsyical memory pages to which this process' virtual memory maps	
	private MemNode[] physMemPages;	

    /** The program being run by this process. */
    protected Coff coff;

    /** This process's page table. */
    protected TranslationEntry[] pageTable;
    /** The number of contiguous pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;
    
    private int initialPC, initialSP;
    private int argc, argv;
	
    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
    
    private static int globalProcessCount;
    
    //count of all processes ever created
    private static int cumProcessCount;
    
    private int processID;
    
    private int parentProcessID;
    
    private UserProcess processWaitingToJoin;
    
    private Map<Integer, UserProcess> childProcesses;
	
    /**
     * Allocate a new process.
     */
    public UserProcess() 
    { 	    
		openFiles = new OpenFile[MAX_OPEN_FILES];
		
		this.childProcesses = new HashMap<Integer, UserProcess>(10);
		
		//setup standard I/O to synchronzied console
		Lib.assertTrue(MAX_OPEN_FILES >= 2);
		
		openFiles[0] = UserKernel.console.openForReading();
		
		openFiles[1] = UserKernel.console.openForWriting();
		
		this.processID = cumProcessCount;
		
		numOpenFiles += 2;	
	}
    
    /**
     * Allocates memory for this process up-front upon
     * process creation. Includes space for the code data
     * from the COFF sections and pages for a stack.
     */
    private boolean allocateMemory()
    {    	
    	if(this.numPages == 0) return false;
    	
    	//create a "blank" virtual address space of size numPages
    	this.physMemPages = new MemNode[this.numPages];
    	
    	for(int i = 0; i < this.numPages; i++)
    	{
    		MemNode memNode = UserKernel.getNextFreeMemPage();
    		
    		if(memNode == null) return false;
    		
    		this.physMemPages[i] = memNode;
    	}   	    	
    	
    	initializeTranslations();
    	
    	return true;
    }
    
    /**
     * Deallocates memory from this process after
     * process is finished or it is killed off.
     */
    private void deallocateMemory()
    {
    	if(this.physMemPages == null) return;
    	
    	Lib.debug('s', "Process deallocating memory...");
    	
    	for(int i= 0; i < this.physMemPages.length; i++)
    	{
    		MemNode nodeToReturn = this.physMemPages[i];
    		
    		UserKernel.returnFreeMemPage(nodeToReturn);
    		
    		this.physMemPages[i] = null;
    	}
    }

    /**
     * Sets up the translation page table that maps this
     * process' virtual mermory page numbers to physical ones.
     */
    private void initializeTranslations()
    {    			
		pageTable = new TranslationEntry[this.numPages];
		
		for (int i=0; i<this.numPages; i++)
		{
			int physPageNum = this.physMemPages[i].endIndex / pageSize; 
					
			pageTable[i] = new TranslationEntry(i,physPageNum, true,false,false,false);
		}	
    }
    
    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return	a new process of the correct class.    	
    	//set up concurrency protections
    	this.freeMemLock = new Lock();   
     */
    public static UserProcess newUserProcess() {
    	
    	globalProcessCount++;
	
    	cumProcessCount++;
    	
    	return (UserProcess)Lib.constructObject(Machine.getProcessClassName());   	
    }

    public int getProcessID(){ return this.processID; }
    
    private void setParentProcessID(int parentID)
    {
    	this.parentProcessID = parentID;
    }
    
    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
		if (!load(name, args))
		{
			handleExit();
			
			return false;
		}
	    
	
	new UThread(this).setName(name).fork();

	return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
	Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param	vaddr	the starting virtual address of the null-terminated
     *			string.
     * @param	maxLength	the maximum number of characters in the string,
     *				not including the null terminator.
     * @return	the string read, or <tt>null</tt> if no null terminator was
     *		found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
	Lib.assertTrue(maxLength >= 0);

	byte[] bytes = new byte[maxLength+1];

	int bytesRead = readVirtualMemory(vaddr, bytes);

	for (int length=0; length<bytesRead; length++) {
	    if (bytes[length] == 0)
		return new String(bytes, 0, length);
	}

	return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
	return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @param	offset	the first byte to write in the array.
     * @param	length	the number of bytes to transfer from virtual memory to
     *			the array.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset,
				 int length) {
	Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);
	
	byte[] memory = Machine.processor().getMemory();
	
	int paddr = translateVAddrToPAdrr(vaddr);

	if (paddr < 0 || paddr >= memory.length)
	    return 0;
	
	int amount = Math.min(length, memory.length-paddr);
	
	System.arraycopy(memory, paddr, data, offset, amount);

	return amount;
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
	return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @param	offset	the first byte to transfer from the array.
     * @param	length	the number of bytes to transfer from the array to
     *			virtual memory.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,
				  int length) {
	Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);
	
	byte[] memory = Machine.processor().getMemory();
	
	//get virtual page number
	int vpn = vaddr / pageSize;
	
	//check to ensure there's a valid virtual page and it's not read only
	if(vpn < 0 || vpn > this.physMemPages.length ||
			this.pageTable[vpn].readOnly) return 0;
	
	int paddr = translateVAddrToPAdrr(vaddr);
	
	// for now, just assume that virtual addresses equal physical addresses
	if (paddr < 0 || paddr >= memory.length)
	    return 0;

	int amount = Math.min(length, memory.length-paddr);
	
	System.arraycopy(data, offset, memory, paddr, amount);

	return amount;
    }
    
    private int translateVPNToPPN(int vpn)
    {
    	for(int i = 0; i < pageTable.length; i++)
    	{
    		if(pageTable[i].vpn == vpn) return pageTable[i].ppn;
    	}
    	
    	return -1;
    }
    
    /**
     * Translates virtual address to physical address.
     * 
     * Returns -1 if vaddress is bad.
     * 
     * @param vaddr
     * @return the translated physical address
     */
    private int translateVAddrToPAdrr(int vaddr)
    {
    	if(vaddr < 0) return -1;
    	
    	//get the virtual page number based on vaddr
    	int vpn = vaddr / pageSize;   	
    	
    	//calculate the offset into the page 
    	int addressOffset = vaddr % pageSize;
    	
    	//get the physical page number
    	int ppn = translateVPNToPPN(vpn);
    	
    	//if the vaddr is outside this process' virtual address space, return 0
    	if(ppn == -1) return -1;
    	
    	//calculate physical address
    	int paddr = (ppn*pageSize) + addressOffset;
    	
    	return paddr;
    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
	Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");
	
	OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
	if (executable == null) {
	    Lib.debug(dbgProcess, "\topen failed");	    
	    return false;
	}

	try {
	    coff = new Coff(executable);
	}
	catch (EOFException e) {
	    executable.close();
	    Lib.debug(dbgProcess, "\tcoff load failed");	    
	    return false;
	}

	// make sure the sections are contiguous and start at page 0
	numPages = 0;
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    if (section.getFirstVPN() != numPages) {
		coff.close();
		Lib.debug(dbgProcess, "\tfragmented executable");		
		return false;
	    }
	    numPages += section.getLength();
	}

	// make sure the argv array will fit in one page
	byte[][] argv = new byte[args.length][];
	int argsSize = 0;
	for (int i=0; i<args.length; i++) {
		
		if(args[i] == null)
		{
			coff.close();
			Lib.debug(dbgProcess, "\tinvalid argument");
		    return false;
		}
		
	    argv[i] = args[i].getBytes();
	    // 4 bytes for argv[] pointer; then string plus one for null byte
	    argsSize += 4 + argv[i].length + 1;
	}
	if (argsSize > pageSize) {
	    coff.close();
	    Lib.debug(dbgProcess, "\targuments too long");	    
	    return false;
	}

	// program counter initially points at the program entry point
	initialPC = coff.getEntryPoint();	

	// next comes the stack; stack pointer initially points to top of it
	numPages += stackPages;
	initialSP = numPages*pageSize;

	// and finally reserve 1 page for arguments
	numPages++;
	
	if(!allocateMemory()) return false;
	
	if (!loadSections()) return false;	

	// store arguments in last page
	int entryOffset = (numPages-1)*pageSize;
	int stringOffset = entryOffset + args.length*4;

	this.argc = args.length;
	this.argv = entryOffset;
	
	for (int i=0; i<argv.length; i++) {
	    byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
	    Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
	    entryOffset += 4;
	    Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
		       argv[i].length);
	    stringOffset += argv[i].length;
	    Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
	    stringOffset += 1;
	}

	return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return	<tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
	if (numPages > Machine.processor().getNumPhysPages()) {
	    coff.close();
	    Lib.debug(dbgProcess, "\tinsufficient physical memory");
	    handleExit();
	    return false;
	}

	// load sections
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    
	    Lib.debug(dbgProcess, "\tinitializing " + section.getName()
		      + " section (" + section.getLength() + " pages)");

	    for (int i=0; i<section.getLength(); i++) {
		
	    	int vpn = section.getFirstVPN()+i;		
		
	    	//set page to readonly in translation table if applicable
	    	this.pageTable[vpn].readOnly = section.isReadOnly();
		
	    	//for now, just assume virtual addresses=physical addresses
	    	int ppn = translateVPNToPPN(vpn);
		
	    	section.loadPage(i, ppn);
	    }
	}
	
	return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
    }    

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
	Processor processor = Machine.processor();

	// by default, everything's 0
	for (int i=0; i<processor.numUserRegisters; i++)
	    processor.writeRegister(i, 0);

	// initialize PC and SP according
	processor.writeRegister(Processor.regPC, initialPC);
	processor.writeRegister(Processor.regSP, initialSP);

	// initialize the first two argument registers to argc and argv
	processor.writeRegister(Processor.regA0, argc);
	processor.writeRegister(Processor.regA1, argv);
    }

    /**
     * Handle the halt() system call. 
     */
    private int handleHalt() {
    	
    	//indicate error if this isn't called by the root process
    	//(only root process can halt the machine)
    	if(globalProcessCount != 1) return -1;
    	
		Machine.halt();
		
		Lib.assertNotReached("Machine.halt() did not halt machine!");
		
		return 0;
    }
    
    
    private int handleCreate(int pathNameVAddress)
    {
    	Lib.debug('s', "UserProcess handling create syscall...");
    	
    	return handleOpen(pathNameVAddress, true);    	
    }
    
    private int setOpenFile(OpenFile fileToSet)
    {    	
    	if(numOpenFiles == MAX_OPEN_FILES) return -1;
    	   	
    	numOpenFiles++;
    	
    	for(int i = 0; i < MAX_OPEN_FILES; i++)
    	{
    		if(openFiles[i] == null)
    		{
    			openFiles[i] = fileToSet;
    			
    			return i;
    		}
    	}
    	
    	//all open file slots are full; return error
    	return -1;   	    	
    }
    
    private int handleExit()
    {    		
    	Lib.debug('s', "UserProcess handling exit...");
    	
    	deallocateMemory();
    	
    	//if it's the last process, kill the machine
    	if(globalProcessCount == 1) handleHalt();
    	
    	globalProcessCount--;
    	
    	return 0;
    }
    
    private int handleOpen(int pathNameVAddress, boolean createIfNotExists)
    {    	
    	Lib.debug('s', "UserProcess handling open...");
    	
    	String fileName =  readVirtualMemoryString(pathNameVAddress, 
    			MAX_FILE_NAME_BYTES);
    	
    	if(fileName == null || fileName.length() == 0) return -1;
    	
    	//if file already exists, return that file handle
    	int fileHandle = getOpenFileHandle(fileName.trim());
    	
    	if(fileHandle != -1) return fileHandle;
    	
    	//process does not have file open; see if exists on file system
    	FileSystem fileSys = Machine.stubFileSystem();
    	
    	OpenFile file = fileSys.open(fileName, createIfNotExists);
    	
    	if(file == null) return -1;
    	
    	fileHandle = setOpenFile(file);
    	
    	Lib.debug('s', "File successfully opened; handle: " + fileHandle);
    	
    	return fileHandle;   	
    }
    
    private int getOpenFileHandle(String fileName)
    {
    	//check if process already has the file open
    	for(int i = 0; i < MAX_OPEN_FILES; i++)
		{
			OpenFile file = openFiles[i];
			
			//if the file names match, return the file handle
			if(file != null && file.getName().trim() == fileName.trim())
				return i;
		}
    	//the file isn't open already
    	return -1;
    }
    
    private OpenFile getOpenFile(int fileDescriptor)
    {
    	if(fileDescriptor >= MAX_OPEN_FILES ||
    			fileDescriptor < 0) return null;
    	
    	return openFiles[fileDescriptor];
    }
    
    private int handleWrite(int fHandle, int bufferVirtualAddress, int size)
    {
    	//TODO: add constraint/check for max size?
    	if(size < 0) return -1;
    	
    	byte[] bytes = new byte[size]; 
    			
        readVirtualMemory(bufferVirtualAddress, bytes);   	    	
    	
        //get open file
        OpenFile file = getOpenFile(fHandle);
        
        if(file == null) return -1;
        
        return file.write(bytes, 0, size);
    }

    private int handleClose(int fileDescriptor)
    {
    	if(fileDescriptor >= MAX_OPEN_FILES || fileDescriptor < 0 ||
    			openFiles[fileDescriptor] == null) return -1;
    	
    	//TODO: need to add clearing write buffers
    	
    	openFiles[fileDescriptor].close();
    	
    	openFiles[fileDescriptor] = null;
    	
    	return 0;    	
    }
    
    private int handleRead(int fileDescriptor, int bufferVAddr, int size)
    {
    	if(size < 0 || fileDescriptor < 0 || 
    			openFiles[fileDescriptor] == null) return -1;
    	
    	OpenFile file = openFiles[fileDescriptor];
    	
    	byte[] readBuffer = new byte[size];
    	
    	int bytesRead = file.read(0, readBuffer, 0, size);
    	
    	//need to add protections for reading/writing size limits    	
    	writeVirtualMemory(bufferVAddr, readBuffer);
    	
    	return bytesRead;
    }
    
    private int handleUnlink(int pathNameVAddress)
    {
    	Lib.debug('s', "UserProcess handling unlink...");
    	
    	String fileName =  readVirtualMemoryString(pathNameVAddress, 
    			MAX_FILE_NAME_BYTES);
    	
    	if(fileName == null || fileName.length() == 0) return -1;
    	
    	int fileHandle = getOpenFileHandle(fileName.trim());
    	
    	OpenFile file = getOpenFile(fileHandle);
    	
    	FileSystem fileSys = Machine.stubFileSystem(); 
    	
    	//if the file doesn't exist, return -1
    	if(file == null && (file = 
    			fileSys.open(fileName, false)) == null)
    	{
    		Lib.debug('s', "File to unlink does not exist");
    		
    		return -1;    	    	
    	}
    	
    	boolean unlinked = fileSys.remove(fileName.trim());
    	
    	Lib.debug('s', "Unlink success: " + (unlinked ? "YES" : "FAILED"));
    	
    	return unlinked ? 0 : -1;
    }
    
    private int handleExec(int progNameVAddress, int numArgs, int argVAddress)
    {
    	String progName =  readVirtualMemoryString(progNameVAddress, 
    			MAX_FILE_NAME_BYTES);
    	
    	if(progName == null || progName.length() == 0) return -1;
    	
    	String[] args = new String[numArgs];   	

    	//get the address of the arguments
    	int[] argAddresses = getArgAddresses(argVAddress, numArgs);   	
    	 	    	   	
    			
    	for(int i = 0; i < argAddresses.length; i++)
    	{  		
    		args[i] = readVirtualMemoryString(argAddresses[i], MAX_FILE_NAME_BYTES);  		    		    		    		
    	}    	
    	
    	UserProcess process = UserProcess.newUserProcess();
    	
    	//set thisp rocess as the parent process
    	process.setParentProcessID(this.processID);
    	
    	//add the new child process to this process' list of childs
    	this.addChildProcess(process);
    	
    	boolean success = process.execute(progName, args);
    	
    	return success ? process.getProcessID() : -1;
    }
    
    private int handleJoin(int pid, int statusVaddr)
    {
    	return -1;
    }
    
    private void addChildProcess(UserProcess childProcess)
    {
    	if(childProcess == null) return;
    	
    	Integer cid = (Integer)childProcess.getProcessID();
    	
    	this.childProcesses.put(cid, childProcess);
    }
    
    private UserProcess removeChildProcess(int childProcessID)
    {
    	return null;
    }
    
    /**
     * Gets the argument addresses by reading from the pointer
     * address. Needs to convert 4 byte address representation
     * to integer.
     * 
     * @param argVAddr
     * @param numArgs
     * @return
     */
    private int[] getArgAddresses(int argVAddr, int numArgs)
    {
    	if(numArgs < 0) return new int[0];
    	
    	int[] argAddrs = new int[numArgs];
    	
    	byte[] argBytes = new byte[4*numArgs];    	
    	
    	int bytesRead = readVirtualMemory(argVAddr, argBytes);   	    	
    	
    	//if reading args unsuccessful, return empty array
    	if(bytesRead != 4*numArgs) return new int[0];
    	
    	ByteBuffer byteWrapper = ByteBuffer.wrap(argBytes);
    	
    	//need to specify endianness
    	byteWrapper.order(ByteOrder.LITTLE_ENDIAN);
    	
    	//convert byte representation of each address to int
    	for(int i = 0; i < numArgs*4; i += 4)
    	{
    		argAddrs[i/4] = byteWrapper.getInt(i);
    	}
    	
    	return argAddrs;
    }
    
    private static final int
        syscallHalt = 0,
	syscallExit = 1,
	syscallExec = 2,
	syscallJoin = 3,
	syscallCreate = 4,
	syscallOpen = 5,
	syscallRead = 6,
	syscallWrite = 7,
	syscallClose = 8,
	syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     * 
     * @param	syscall	the syscall number.
     * @param	a0	the first syscall argument.
     * @param	a1	the second syscall argument.
     * @param	a2	the third syscall argument.
     * @param	a3	the fourth syscall argument.
     * @return	the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
	switch (syscall) {
	case syscallHalt:
	    return handleHalt();
	case syscallCreate:
		return handleCreate(a0);
	case syscallExit:
		return handleExit();
	case syscallOpen:
		return handleOpen(a0, false);
	case syscallWrite:
		return handleWrite(a0, a1, a2);
	case syscallClose:
		return handleClose(a0);
	case syscallRead:
		return handleRead(a0, a1, a2);
	case syscallUnlink:
		return handleUnlink(a0);
	case syscallExec:
		return handleExec(a0, a1, a2);
	case syscallJoin:
		return handleJoin(a0, a1);

	default:
	    Lib.debug(dbgProcess, "Unknown syscall " + syscall);
	    Lib.assertNotReached("Unknown system call!");
	}
	return 0;
    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param	cause	the user exception that occurred.
     */
    public void handleException(int cause) {
	Processor processor = Machine.processor();

	switch (cause) {
	case Processor.exceptionSyscall:
	    int result = handleSyscall(processor.readRegister(Processor.regV0),
				       processor.readRegister(Processor.regA0),
				       processor.readRegister(Processor.regA1),
				       processor.readRegister(Processor.regA2),
				       processor.readRegister(Processor.regA3)
				       );
	    processor.writeRegister(Processor.regV0, result);
	    processor.advancePC();
	    break;				       
				       
	default:
	    Lib.debug(dbgProcess, "Unexpected exception: " +
		      Processor.exceptionNames[cause]);
	    Lib.assertNotReached("Unexpected exception");
	}
    }

}
