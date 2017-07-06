/* unlinkTest.c
 * 	Program that test unlinking a file in lucos
 *	by calling the unlink() system call.
 */

#include "syscall.h"

int main()
{ 
    char *fName = "unlTest1.txt";
  
    unlink(fName);
}
