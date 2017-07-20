/*  execTest.c
 *	Program that tests exec() syscall in lucOS
 * 	by creating new processes.
 */

int NUM_PROGRAMS = 1;

int main()
{
    char *file = "simpleHello.coff";
   
    int argc = 3;

    char *argv[] = {"Hello, world!", "test", "longfilename123456789101112"};

    int prog_cnt;

    for(prog_cnt = 0; prog_cnt < NUM_PROGRAMS; prog_cnt++)
    {
   
        int newPID1 = exec(file, argc, argv);

        //Add slight delay to printing success message
        int dummy = 1;

        while(dummy++ < 9999);

        printf("process created, pid is %d\n", newPID1);
    }
}
