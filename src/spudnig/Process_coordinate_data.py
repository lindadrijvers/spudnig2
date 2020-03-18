# -*- coding: utf-8 -*-
"""
This script should import the coordinates saved by the OpenPoseVideo code, 
calculate a displacement vector for each hand, and create a single video plot.


Created on Thu Jan 10 16:43:28 2019

@author: James Trujillo
"""

import csv
import cv2
import numpy as np
import matplotlib.pyplot as plt
from matplotlib import animation
import math
import os
import pdb
import ffmpeg

plt.rc('xtick', labelsize=12)
plt.rc('ytick', labelsize=12)

# these will store the actual coordinates
L_Hand = []
R_Hand = []

FPS = 29

#our directory
mainDir = "//data/cosi/workspaces/cosi-coact/working_data/OpenPose (Jordy)/openpose/"
DataFolders = os.listdir(mainDir)

# filenames where the data is stored
#FILENAMES = ['Left_Hand_t.csv', 'Right_Hand_t.csv']
#HANDS = ['L_Hand', 'R_Hand']
#we'll use the files generated for Jordy's project, which use different filenames
FILENAMES = ['hand_left_sample.csv', 'hand_right_sample.csv']
HANDS = ['L_Hand', 'R_Hand']

# hasFrame, frame = cap.read() set up the video writer vid_writer = cv2.VideoWriter('output_vel_curves.avi',
# cv2.VideoWriter_fourcc('M','J','P','G'), 29, (frame.shape[1],frame.shape[0]))

def import_coordinates(csv_file, Hand_variable):
    # open our file
    with open(csv_file) as CSV_File:
        csv_reader = csv.reader(CSV_File)
        # put all the data into a variable
        for row in csv_reader:
            # the if statement skips blank lines
            if row:
                Hand_variable.append(row[0:2])
    return (Hand_variable)


def calculate_distance(Hand):
    """
    This just calculates the displacement between each set of points, then the
    velocity from the displacement.
    """
    IDX = 0
    dist = []
    vel = []
    for coords in Hand[1:]:
        P_coords = Hand[IDX]
        # first calculate displacement
        DISPLACE = math.hypot(float(coords[0]) - float(P_coords[0]), float(coords[1]) - float(P_coords[1]))
        dist.append(DISPLACE)
        # then calculate velocity
        vel.append(DISPLACE / FPS)
        IDX += 1
    return (dist, vel)


def init():
    """
    This initializes each frame - basically the blank plots that we will draw
    on with each call of the animate argument. All style options should be
    specified here.
    """

    ax1 = fig.add_subplot(2, 1, 1)
    ax2 = fig.add_subplot(2, 1, 2)

    ax1.set_ylabel('Velocity \n(pixels per sec)', fontsize=15)
    ax1.set_title('Left Hand Velocity Curve', fontsize=20)
    ax2.set_xlabel('Time (frames)', fontsize=15)
    ax2.set_title('Right Hand Velocity Curve', fontsize=20)
    plt.subplots_adjust(hspace=0.5)

    # ax1.plot(L_vel)
    # ax2.plot(R_vel)

    ax1.set_ylim(np.amin(L_vel) - 0.5, np.amax(L_vel) + 0.5)
    ax2.set_ylim(np.amin(R_vel) - 0.5, np.amax(R_vel) + 0.5)

    return ax1, ax2



def animate(i):
    if i < len(L_vel):
        E = i
    else:
        E = len(L_vel)
    ax1.plot(L_vel[E])
    ax1.set_xlim(int(i) - 30, int(i) + 30)
    ax2.set_xlim(int(i) - 30, int(i) + 30)

def main():

    for file in FILENAMES:
        if "left" in file:
            L_Hand = import_coordinates(mainDir + DataFolders[0] + "/" + file, L_Hand)
        else:
            R_Hand = import_coordinates(mainDir + DataFolders[0] + "/" + file, R_Hand)



    # now go through and calculate the displacement vector
    L_dist, L_vel = calculate_distance(L_Hand)
    R_dist, R_vel = calculate_distance(R_Hand)

    # make some plots
    #plt.plot(L_vel)
    #plt.xlabel('Time (frames)',fontsize=15)
    #plt.ylabel('Velocity (pixels per sec',fontsize=15)
    #plt.title('Left Hand Velocity Curve',fontsize=20)
    #plt.xlim([20,40])

    fig, (ax1, ax2) = plt.subplots(2,1)
    ax1.plot(L_vel)
    ax1.set_ylabel('Velocity \n(pixels per sec)',fontsize=15)
    ax1.set_title('Left Hand Velocity Curve',fontsize=20)
    ax2.plot(R_vel)
    ax2.set_xlabel('Time (frames)',fontsize=15)
    ax2.set_title('Right Hand Velocity Curve',fontsize=20)
    plt.subplots_adjust(hspace=0.5)

# write to video file
# write loop here to go through, changing x lim
    fig, (ax1, ax2) = plt.subplots(2, 1)

   # pdb.set_trace()

    #init()
#this will not work
ani = animation.FuncAnimation(fig, animate, frames = 10)

#this should work, but requires ffmpeg installation

# Set up formatting for the movie files
Writer = animation.writers['ffmpeg']
writer = Writer(fps=15, metadata=dict(artist='Me'), bitrate=1800)

ani.save('anim.mp4')
plt.show()

    ##different attempt

 #   FFMpegWriter = animation.writers['ffmpeg']
 #   metadata = dict(title='Movie Test', artist='Matplotlib',
  #                  comment='Movie support!')
 #   writer = FFMpegWriter(fps=15, metadata=metadata)
#
   # with writer.saving(fig, "writer_test.mp4", 100):
  #      for i in range(80):
  ##          animate(i)
  #          writer.grab_frame()


main()
