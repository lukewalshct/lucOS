/*  execTest.c
 *	Program that tests exec() syscall in lucOS
 * 	by creating new processes.
 */

int main()
{
    char *file = "simpleHello.coff";

    int argc = 0;

    char *argv[] = {};

    int newPID1 = exec(file, argc, argv);

    printf("process created, pid is %d", newPID1);
}
