int main (int a)
{
   int a = 10;

   LOOP:do
   {
      if( a == 15)
      {
         a = a + 1;
         goto LOOP;
      }
      a++;
   }while( a < 20 );
   return 0;
}