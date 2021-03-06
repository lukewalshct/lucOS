/* readTest.c
 *	Program that tests reading a file in lucos
 *	by calling the read() system call.
 */

#include "syscall.h"

int testRead(char* fName, char* buffer, int size, char* expected, int compare);
void verifyResult(char* buffer, char* expected, int bytesRead, int compare);
void throwReadError();

int main()
{
    //normal read from a file
    char buff1[10];
    testRead("rTest1.txt", buff1, 10, "0123456789", 1);

    //read from a file w/text, but size 0 - should read 0
    char buff2[0];
    testRead("rTest1.txt", buff2, 0, "", 1); 

    //read from file w/ no text - shoud read 0
    char buff3[10];
    testRead("rTest2.txt", buff3, 10, "", 1);

    //read from file buffer smaller than read size - should
    char buff4[5];
    testRead("rTest1.txt", buff4, 10, "0123456789", 1);

    //read from vaddress buffer that is out of virtual mem
    testRead("rTest1.txt", 9999999, 10, "", 0);
}

int testRead(char *fName, char *buffer, int size, char* expected, int compare)
{
    int fHandle = open(fName);

    if(fHandle == -1)
    {
        printf("File does not exist");

        return -1;
    }
    else
    {
        int bytesRead = read(fHandle, buffer, size);

        close(fHandle);

        verifyResult(buffer, expected, bytesRead, compare);

        return bytesRead;
    }

}

void verifyResult(char* buffer, char* expected, int read, int compare)
{
    if(!compare) return;

    printf("\nRead test expected result:\n");

    printf(expected);
    
    printf("\nRead test actual result:\n");

    printf(buffer);

    printf("\n");

    int i;

    if(strlen(expected) != strlen(buffer) &&
        (strlen(expected) != read)) throwReadError();

    for(i = 0; i < strlen(expected); i++)
    {
        if(buffer[i] != expected[i]) throwReadError();        
    }     

}

void throwReadError()
{
    printf("!!!!READ TEST ERROR!!!");

    halt();
}
