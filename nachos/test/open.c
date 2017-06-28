/* open.c
 *	Program that tests opening a file in lucos by 
 *      calling the open() system call
 */

#include "syscall.h"

char *fName = "testFile1.txt";

int main()
{
   
   printf("user program opening file %s ...", *fName);

   int fileHandle = open(fName);

   printf("%s opened with file handle %d", *fName, fileHandle);

   halt(); 
}


