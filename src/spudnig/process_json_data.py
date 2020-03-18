# -*- coding: utf-8 -*-
"""
Imports the json files and collects separate variables for each joint of interest.
These are used for subsequent Analyze script, and are also saved for data inspection.

Output: Velocity vectors for each hand; tracking-confidence for each hand; velocity & confidence plots


Created on Tue Jan 29 16:01:29 2019
@author: James Trujillo
"""

import os
import json
import matplotlib.pyplot as plt
import math
import csv
import numpy as np
import json
import pandas as pd
#requires: pillow


def main():
    OP_dir = "C:/Users/jamtru/Documents/Python Scripts/inputs/"
    #OP_dir = "//data/cosi/workspaces/cosi-coact/working_data/OpenPose (Jordy)/openpose/"
    os.chdir(OP_dir)
    
    #general directory
    dirs = os.listdir(OP_dir)
    
    FPS = 25
    
    for Vid in dirs:
        if os.path.isdir(Vid):
            files = os.listdir(Vid)
            R_finger = [] #initialize
            L_finger = []
            R_Hand = []
            L_Hand = []
            RF_conf = []
            LF_conf = []
            RH_conf = []
            LH_conf = []
            Nose = []
            Neck = []
            MidHip = []
            REye = []
            LEye = []
            LElb = []
            RElb = []
            LHip = []
            RHip = []
            LEar = []
            REar = []
            
            for file in files:
                if file.endswith(".json"):
                    filename = OP_dir + Vid + '\\' + file
            
                    #load the data. keypoints are given as x,y,confidence
                    with open(filename, 'r') as f:
                        datastore = json.load(f)
                   
                    #store the Fingertip Points   
                    x = datastore["people"][0]["hand_right_keypoints_2d"][8*3]#8 is the index of the R fingertip
                    y = datastore["people"][0]["hand_right_keypoints_2d"][(8*3)+1]
                    R_finger.append([x,y])
                    x = datastore["people"][0]["hand_left_keypoints_2d"][8*3]
                    y = datastore["people"][0]["hand_left_keypoints_2d"][(8*3)+1]
                    L_finger.append([x,y])
                    
                    #store confidence
                    RF_conf.append(datastore["people"][0]["hand_right_keypoints_2d"][(8*3)+2])
                    LF_conf.append(datastore["people"][0]["hand_left_keypoints_2d"][(8*3)+2])
                    #-----
                    
                    #store the Hand Points  
                    x = datastore["people"][0]["hand_right_keypoints_2d"][0]#0 is the index of the R wrist
                    y = datastore["people"][0]["hand_right_keypoints_2d"][(0)+1]
                    R_Hand.append([x,y])
                    x = datastore["people"][0]["hand_left_keypoints_2d"][0]
                    y = datastore["people"][0]["hand_left_keypoints_2d"][(0)+1]
                    L_Hand.append([x,y])
                    
                    #store confidence
                    RH_conf.append(datastore["people"][0]["hand_right_keypoints_2d"][(0)+2])
                    LH_conf.append(datastore["people"][0]["hand_left_keypoints_2d"][(0)+2])

                    #store body points
                    x = datastore["people"][0]["pose_keypoints_2d"][0]  # 0 = Nose
                    y = datastore["people"][0]["pose_keypoints_2d"][(0) + 1]
                    Nose.append([x, y])
                    x = datastore["people"][0]["pose_keypoints_2d"][1*3]  # 1 = Neck (Mid shoulder)
                    y = datastore["people"][0]["pose_keypoints_2d"][(1*3) + 1]
                    Neck.append([x, y])
                    x = datastore["people"][0]["pose_keypoints_2d"][8 * 3]  # 8 = Mid Hip
                    y = datastore["people"][0]["pose_keypoints_2d"][(8 * 3) + 1]
                    MidHip.append([x, y])
                    x = datastore["people"][0]["pose_keypoints_2d"][15 * 3]  # 15 = REye
                    y = datastore["people"][0]["pose_keypoints_2d"][(15 * 3) + 1]
                    REye.append([x, y])
                    x = datastore["people"][0]["pose_keypoints_2d"][16 * 3]  # 16 = LEye
                    y = datastore["people"][0]["pose_keypoints_2d"][(16 * 3) + 1]
                    LEye.append([x, y])
                    x = datastore["people"][0]["pose_keypoints_2d"][3 * 3]  # 3 = RElb
                    y = datastore["people"][0]["pose_keypoints_2d"][(3 * 3) + 1]
                    RElb.append([x, y])
                    x = datastore["people"][0]["pose_keypoints_2d"][6 * 3]  # 6 = LElb
                    y = datastore["people"][0]["pose_keypoints_2d"][(6 * 3) + 1]
                    LElb.append([x, y])
                    x = datastore["people"][0]["pose_keypoints_2d"][12 * 3]  # 12 = LHip
                    y = datastore["people"][0]["pose_keypoints_2d"][(12 * 3) + 1]
                    LHip.append([x, y])
                    x = datastore["people"][0]["pose_keypoints_2d"][9 * 3]  # 9 = RHip
                    y = datastore["people"][0]["pose_keypoints_2d"][(9 * 3) + 1]
                    RHip.append([x, y])
                    x = datastore["people"][0]["pose_keypoints_2d"][18 * 3]  # 18 = LEar
                    y = datastore["people"][0]["pose_keypoints_2d"][(18 * 3) + 1]
                    LEar.append([x, y])
                    x = datastore["people"][0]["pose_keypoints_2d"][17 * 3]  # 17 = REar
                    y = datastore["people"][0]["pose_keypoints_2d"][(17 * 3) + 1]
                    REar.append([x, y])

                #  after collecting all of the coordinate & confidence data, we need to get displacement
                LF_dist, LF_vel  = calculate_distance(L_finger,FPS)
                RF_dist, RF_vel  = calculate_distance(R_finger,FPS)
                
                LH_dist, LH_vel  = calculate_distance(L_Hand,FPS)
                RH_dist, RH_vel  = calculate_distance(R_Hand,FPS)

            #  make a dataframe

            df = pd.DataFrame(list(zip(R_Hand,L_Hand, R_finger, L_finger, Nose,Neck,MidHip,REye,LEye,RElb,LElb,
                                       LHip, RHip)),
                              columns=['R_Hand','L_Hand','R_finger','L_finger','Nose','Neck','MidHip','REye','LEye',
                                       'RElb', 'LElb', 'LHip', 'RHip'])

            print('making plots')
            make_vel_conf_plots(LF_vel, RF_vel, LF_conf, RF_conf, 'finger',OP_dir+Vid)
            make_vel_conf_plots(LH_vel, RH_vel, LH_conf, RH_conf, 'finger',OP_dir+Vid)
           
            
            #write to file
            write_data_to_file(RF_vel, '\\Right_finger_vel.csv', OP_dir+Vid)
            write_data_to_file(LF_vel, '\\Left_finger_vel.csv', OP_dir+Vid)
            write_data_to_file(RF_conf, '\\Right_finger_conf.csv', OP_dir+Vid)
            write_data_to_file(LF_conf, '\\Left_finger_conf.csv', OP_dir+Vid)
            
            write_data_to_file(RH_vel, '\\Right_hand_vel.csv', OP_dir+Vid)
            write_data_to_file(LH_vel, '\\Left_hand_vel.csv', OP_dir+Vid)
            write_data_to_file(RH_conf, '\\Right_hand_conf.csv', OP_dir+Vid)
            write_data_to_file(LH_conf, '\\Left_hand_conf.csv', OP_dir+Vid)

            plt.close('all')

            return df


def calculate_distance(Hand, FPS):
    """
    This just calculates the displacement between each set of points, then the
    velocity from the displacement.
    """
    IDX = 0
    dist = []
    vel = []
    for coords in Hand[1:]:
        Prev_coords = Hand[IDX]
        #first calculate displacement
        DISPLACE = math.hypot(float(coords[0]) - float(Prev_coords[0]), float(coords[1]) - float(Prev_coords[1]))
        dist.append(DISPLACE)
        #then calculate velocity
        vel.append(DISPLACE*FPS)
    return(dist, vel)
    
def write_data_to_file(data, filename, path):
    fullfile = path + filename
    output_array = np.array(data)
    np.savetxt(fullfile, output_array, delimiter="\t")


def make_vel_conf_plots(data_L, data_R, conf_L, conf_R, source, path):
    plt.figure(figsize=(20,10))
    
    #Right affector velocity
    plt.subplot(2,2,1)
    plt.plot(data_R)
            
    plt.xlabel('frame',fontsize=20)
    plt.ylabel('velocity (px per s)',fontsize=20)
    plt.title(('Right ' + source + ' velocity'),fontsize=24)

    #Right affector confidence
    plt.subplot(2,2,2)
    plt.plot(conf_R)
            
    plt.xlabel('frame',fontsize=20)
    plt.ylabel('confidence',fontsize=20)
    plt.title(('Right ' + source + ' confidence'),fontsize=24)
    
    #Left affector velocity
    plt.subplot(2,2,3)
    plt.plot(data_L)
            
    plt.xlabel('frame',fontsize=20)
    plt.ylabel('velocity (px per s)',fontsize=20)
    plt.title(('Left ' + source + ' velocity'),fontsize=24)
    
    #left affector confidence
    plt.subplot(2,2,4)
    plt.plot(conf_L)
            
    plt.xlabel('frame',fontsize=20)
    plt.ylabel('confidence',fontsize=20)
    plt.title(('Left ' + source + ' confidence'),fontsize=24)
            
    plt.subplots_adjust(hspace=0.7)
            
    figname = path + '\\velocity_profile_' + source + '.jpg'
    plt.savefig(figname)
    
    plt.gcf().clear()
    plt.close()

main()

# make sure this is not run when imported
#if __name__ == "__main__":
#    import sys
    #df = main()
