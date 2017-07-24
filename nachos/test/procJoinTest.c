/*   procJoinTest.c
 *	Program that tests join() syscall
 */

void delay(int i);

int main()
{
    printf("starting proc join test\n");

    delay(9999);    

    char *file = "workSim.coff";

    char *argv[] = {"0", "0"};

    int childID = exec(file, 2, argv);

    int *status;

    int result1 = join(childID, status);

    delay(9999);

    char *argv2[] = {"1", "1"};

    int childID2 = exec(file, 2, argv2);

    int result2 = join(childID2, status);

    delay(9999);

    printf("join result1: %d\n", result1);

    printf("join result2; %d\n", result2);

    printf("finishing proc join test\n");    
}

void delay(int i)
{
    int start = 0;

    while(start++ < i);
} 

