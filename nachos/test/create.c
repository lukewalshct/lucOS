/* create.c
 *	Program that tests creation of a file in lucos,
 *	specifically by calling the creat() system call.
 */

#include "syscall.h"

int main()
{
    creat("test1");
} 


