/* create.c
 *	Program that tests creation of a file in lucos,
 *	specifically by calling the creat() system call.
 */

#include "syscall.h"

int numFilesToCreate = 20;

void testCreate();

int main()
{
    printf("Initialize creat() syscall tests...");

    printf("Attempting to create %d files simultaneously");

    testCreate();

} 

void testCreate()
{
    int i;

    for(i = 0; i < numFilesToCreate; i++)
    {
        char fName[256];
 
        sprintf(fName, "cTest_%d.txt", i);

        creat(fName);
    }

}




