# HFSize
create a raw HFS image containing a single file

You will need a jdk and gradle or an IDE


### If using gradle:

from the project directory do:

gradle clean jar

then

java -cp build/libs/HFSize.jar HFSize /path/to/file.sit

the result will be in
              
volume.img

### If using an IDE:
                                
import the project as a gradle project

remove the @ignore from the test and change the path

run the test, the result will be in

volume.img