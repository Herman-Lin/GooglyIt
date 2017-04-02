package lahacks.herman.googlyit;

import com.microsoft.projectoxford.face.*;
import com.microsoft.projectoxford.face.contract.*;

public class Global {
        public static int mode = 0; // 0 = main, 1 = Googly; 2 = Swap
        public static Face[] all;
        public static boolean firstCall = true;
        public static int time;
        public static double r = -90; // rotation
        public static double d = 1; // direction  1 = clockwise ; -1 = counterclockwise
        public static double rv = -d * r * 4; // rotational velocity; Deg/Sec

}
