# This script demonstrates the libration of the Moon as viewed from just 
# above the Earth's surface. 
# Created by Svetlin Tassev.

# Note:
# In order for the script to run well, wait for it to finish setting 
# up the camera mode to free.
# Also, you may want to hide the gaia star data, as that slows down 
# gaiasky quite a bit and makes the simulation choppy.

from py4j.clientserver import ClientServer, JavaParameters, PythonParameters
import time
import numpy as np

class CameraUpdaterRunnable():

    def __init__(self):
        self.rBAold = []
        self.rCA_tied = [] # camera coordinates in frame "tied" with A and B
        self.u_tied = [] # camera Up
        self.dir_tied = [] # camera dir
        self.rCA0 = [] # original cam coordinates relative to A
        self.lastt = 0.0
        self.cam_coo=False # do we have the camera settings in the "tied" coordinates?
        self.simT=gs.getSimulationTime()
    def run(self):
        eps=1.e-32
        Ap = gs.getObjectPredictedPosition("Earth")
        Bp = gs.getObjectPredictedPosition("Moon")
        Ap0 = gs.getObjectPosition("Earth")
        Bp0 = gs.getObjectPosition("Moon")
        
        Cp = gs.getCameraPosition()
        Cp=np.array([Cp[0]/1.e6,Cp[1]/1.e6,Cp[2]/1.e6]) # convert to object position units
	    
        U = gs.getCameraUp()

        rBA = np.array([Bp[0] - Ap[0], Bp[1] - Ap[1], Bp[2] - Ap[2]])
        rBA0 = np.array([Bp0[0] - Ap0[0], Bp0[1] - Ap0[1], Bp0[2] - Ap0[2]])
        rCA0 = np.array([Cp[0] - Ap0[0], Cp[1] - Ap0[1], Cp[2] - Ap0[2]])
        rCA = np.array([Cp[0] - Ap[0], Cp[1] - Ap[1], Cp[2] - Ap[2]])
        r2 = []
        
        # See if camera position needs refreshing every dt sec

        currt = time.time()
                
        dt=0.1
        if currt - self.lastt >= dt:
            if ((gs.getSimulationTime()-self.simT)==0):# if time is paused, reset camera
                self.cam_coo=False
                self.lastt=currt
                
        do=False # should we be moving the camera? Not yet.
        
        #dt1=0.000
        if (((gs.getSimulationTime()-self.simT)!=0)): # and (currt - self.lastt >= dt1)):
            r1 = rBA.copy() #(rBA+rBA0)/2. #rBA.copy()
            r2 = rBA-rBA0 #self.rBAold
            
            r10 = rBA0.copy() #(rBA+rBA0)/2. #rBA.copy()
            r20 = rBA-rBA0 #self.rBAold
            
            do = ((r2.dot(r2))/(r1.dot(r1))>eps)   # check that objects moved relative to each other. If not then camera won't be moved.
            
            # Unless objects moved towards each other, we can set up our orthonormal coordinate system.
            if (do):  
                do = False # Wait to make sure objects didn't move towards each other.
                #Set the normalized vectors
                r2/=np.sqrt(r2.dot(r2))
                r1/=np.sqrt(r1.dot(r1))
                r2=np.cross(r1,r2)   # make r2 orthogonal to r1
                #r2=r2-r1.dot(r2)*r1 # make orthogonal
                if (r2.dot(r2)>eps): # if objects move towards each other, this would fail.
                    r2/=np.sqrt(r2.dot(r2)) #normalize
                    r3 = np.cross(r1,r2) # get third unit vector
                    r3/=np.sqrt(r3.dot(r3))
                    do=True # okay, coordinate system is set up, so move on with camera move below
                #Set the normalized vectors0
                r20/=np.sqrt(r20.dot(r20))
                r10/=np.sqrt(r10.dot(r10))
                r20=np.cross(r10,r20)   # make r2 orthogonal to r1
                #r2=r2-r1.dot(r20)*r1 # make orthogonal
                if (r20.dot(r20)>eps): # if objects move towards each other, this would fail.
                    r20/=np.sqrt(r20.dot(r20)) #normalize
                    r30 = np.cross(r10,r20) # get third unit vector
                    r30/=np.sqrt(r30.dot(r30))
                    do=True # okay, coordinate system is set up, so move on with camera move below
            # Save previous time
            # Save previous time
            self.lastt = currt
 
        self.simT=gs.getSimulationTime()
        
        # Setting up the components of camera dir, up and position (rCA) in the "tied" reference frame
        # We will use these to rotate the camera direction and FoV in the next frames ... until the 
        # time is paused (self.cam_coo=False). When that happens, we will recalculate this.
        if (do and not(self.cam_coo)): 
            #Let's calculate camera coordinates
            rCAm=rCA0 #(rCA0+rCA)/2. #rCA0
            self.rCA_tied=np.array([rCAm.dot(r10), rCAm.dot(r20), rCAm.dot(r30)])
            #self.rCA_tied=np.array([self.rCA0.dot(r1), self.rCA0.dot(r2), self.rCA0.dot(r3)])
            # Cam dir
            rdir = gs.getCameraDirection()
            rdir = np.array([rdir[0],rdir[1],rdir[2]])
            rdir/=np.sqrt(rdir.dot(rdir))#normalize camera direction vector
            self.dir_tied=np.array([rdir.dot(r10), rdir.dot(r20), rdir.dot(r30)])
            
            # Cam Up
            ra=(r10+r20+r30) # this is random: a vector that's preferably not aligned with any ri0. Hopefully, this would ensure no division of zero happens below.
            ra/=np.sqrt(ra.dot(ra))
            u1=np.cross(ra,rdir)
            u1 /= np.sqrt(u1.dot(u1)) # This may need some rework to check that we are not dividing by ~0.
            u2 = np.cross(u1,rdir)
            u2 /= np.sqrt(u2.dot(u2))
            self.u_tied=np.array([u1.dot(U),u2.dot(U)])
            
            self.cam_coo=True # cam set up done.
        
        #While time is running, move camera and rotate Up and direction vectors using the
        # components calculated in the preceding if statement:
        if (do and self.cam_coo):
            campos = self.rCA_tied[0]*r1 + self.rCA_tied[1]*r2 + self.rCA_tied[2]*r3
            Am=np.array([Ap[0],Ap[1],Ap[2]])
            campos+= Am
            campos*=1.e6 #convert to camera position units
            
            gs.setCameraPosition(campos, True)
            
            #set cam dir
            gs.setCameraDirection(self.dir_tied[0]*r1+self.dir_tied[1]*r2+self.dir_tied[2]*r3, True)
            
            #set cam up
            rdir = gs.getCameraDirection()
            rdir = np.array([rdir[0],rdir[1],rdir[2]])
            rdir/=np.sqrt(rdir.dot(rdir))#normalize camera direction vector
            ra=(r1+r2+r3) # Same random vector as above.
            ra/=np.sqrt(ra.dot(ra))
            u1=np.cross(ra,rdir)
            u1 /= np.sqrt(u1.dot(u1))
            u2 = np.cross(u1,rdir)
            u2 /= np.sqrt(u2.dot(u2))
            gs.setCameraUp(self.u_tied[0]*u1+self.u_tied[1]*u2, True)
            
    def toString():
        return "camera-update-runnable"

    class Java:
        implements = ["java.lang.Runnable"]

gateway = ClientServer(java_parameters=JavaParameters(auto_convert=True),
                      python_parameters=PythonParameters())
gs = gateway.entry_point

gs.cameraStop()

gs.stopSimulationTime()
gs.setVisibility("element.milkyway", False)
gs.setVisibility("element.orbits", False)
gs.setVisibility("element.others",False)


gs.setCameraOrientationLock(False)
gs.setCameraLock(True)

gs.setFov(2.0)

gs.setSimulationTime(2022, 9, 29, 0, 0, 0, 0)
gs.setSimulationPace(5e5)
gs.sleep(1)

gs.setCameraFocus("Moon")
Ap = gs.getObjectPredictedPosition("Earth")
Bp = gs.getObjectPredictedPosition("Moon")
# place camera ~7Mm away from earth (just outside earth's atmosphere), assuming moon is ~400Mm away from earth.
gs.setCameraPosition(np.array([((Bp[i]-Ap[i])*7./400.+Ap[i]) for i in range(3)])*1.e6,True) 
gs.setCameraFocus("Moon")

gs.sleep(4)
gs.setCameraFree()

# park the camera updater
cameraUpdater = CameraUpdaterRunnable()
gs.parkCameraRunnable("camera-updater", cameraUpdater)

gs.hideDataset("Gaia DR3 large")
gs.sleep(2)
gs.startSimulationTime()

#gs.startSimulationTime()

#gs.sleep(10)
#gs.setVisibility("element.orbits", False)
gs.sleep(40)

gs.stopSimulationTime()

# clean up and finish
gs.cameraStop()
gs.sleep(1.0)
gs.unparkRunnable("camera-updater")
gs.sleep(1.0)


# Finish flushing
gs.sleepFrames(4)

gs.maximizeInterfaceWindow()
gs.enableInput()

# close connection
gateway.shutdown()
