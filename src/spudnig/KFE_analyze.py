# -*- coding: utf-8 -*-
"""
Kinematic Feature Extraction
Based on the Matlab scripts of the same name, described in Trujillo et al., 2018
Beh. Res. Meth.
Takes OpenPose-generated (2D) coordinate data and outputs summary tables and figures.
# Runs on Python 3.7 for matplotlib compatibility

Created on Thu Jan 31 11:03:09 2019
Last update: Feb. 11. 2020

@author: James Trujillo
jptrujillo88@hotmail.com
"""


# TODO: come up with a standardized measure - pixels are not helpful for defining any thresholds!
# TODO: add plotting
# TODO: add wrapper and output


# Run check_skeleton(df) for a quick plot of all joints on the first frame

#import pandas as pd
import statistics
from scipy import signal
import math
import numpy as np
import process_json_data
import matplotlib.pyplot as plt
import os
import json


def main(OP_dir, annot_dir, FPS):
    OP_dir = "C:/Users/James/Work/SkypeProj/data/"

    # first, a loop to go through all the data. Take video name as starting point, match to annotation file
    os.chdir(OP_dir)
    # UP TO HERE WORKS

    #general directory
    dirs = os.listdir(OP_dir)
    for Vid in dirs:
        df = process_json_data.main((OP_dir + Vid))
    # here we need to split the dataframe according to some other data

    FPS = 25 # remove this once the wrapper is working !!!
    dur = df.shape[0]  # number of datapoints

    # first, we flip the y-axis, if needed
    if df['Nose'][0][1] < df['MidHip'][0][1]:
        cols = list(df)
        # first get the vertical height of the configuration
        # we only do this for the first frame; the transformation will be applied to all frames
        maxpoint = []
        for joint in cols:
            maxpoint.append(df[joint][0][1])
        # iterate over each joint, in each frame, to flip the y-axis
        for frame in range(len(df)):
            for joint in cols:
                ytrans = max(maxpoint) - df[joint][frame][1] - 1
                df[joint][frame][1] = ytrans

    L_subs, R_subs, subslocs_L, subslocs_R = calc_submoves(df, FPS)  # get submovements
    maxheight = calc_vert_height(df)  # get vertical height
    Lmax, Rmax = calc_maxSize(df)  # get size from body
    LSize, RSize, Luse, Ruse = calc_jointSize(df, Lmax, Rmax)  # get number of joints used
    Lsubs, Rsubs, L_peaks, R_peaks = calc_submoves(df, FPS)  # get number of submovements
    peakL = calc_peakVel(df['L_Hand'], FPS)  # left hand peak velocity
    peakR = calc_peakVel(df['R_Hand'], FPS)  # right hand peak velocity
    hold_count, hold_time, hold_avg = calc_holds(df, Ruse, Luse, L_peaks, R_peaks, FPS) # holds
    vol = calc_volume_size(df)
    space_use_L, space_use_R, mcneillian_maxL, mcneillian_maxR, mcneillian_modeL, mcneillian_modeR = calc_mcneillian_space(df)


def calculate_distance(Hand, FPS):
    """
    This just calculates the displacement between each set of points, then the
    velocity from the displacement.
    ### CHECKED, works
    """
    IDX = 0
    dist = []
    vel = []
    for coords in Hand[1:]:
        Prev_coords = Hand[IDX]
        # first calculate displacement
        DISPLACE = math.hypot(float(coords[0]) - float(Prev_coords[0]), float(coords[1]) - float(Prev_coords[1]))
        dist.append(DISPLACE)
        # then calculate velocity
        vel.append(DISPLACE * FPS)

        IDX = IDX + 1
    dist = list(dist)
    vel = list(vel)
    return dist, vel


def calc_vert_height(df):
    # Vertical amplitude
    # H: 0 = below midline;
    #    1 = between midline and middle-upper body;
    #    2 = above middle-upper body, but below shoulders;
    #    3 = between shoulders nad middle of face;
    #    4 = between middle of face and top of head;
    #    5 = above head

    H = []
    for index, frame in df.iterrows():
        SP_mid = ((df.loc[index, "Neck"][1] - df.loc[index, "MidHip"][1]) / 2) + df.loc[index, "MidHip"][1]
        Mid_up = ((df.loc[index, "Nose"][1] - df.loc[index, "Neck"][1]) / 2) + df.loc[index, "Neck"][1]
        Eye_mid = (df.loc[index, "REye"][1] + df.loc[index, "LEye"][1] / 2)  # mean of the two eyes vert height
        Head_TP = ((df.loc[index, "Nose"][1] - Eye_mid) * 2) + df.loc[index, "Nose"][1]
        if df.loc[index, "R_Hand"][1] > SP_mid or df.loc[index, "L_Hand"][1]:
            if df.loc[index, "R_Hand"][1] > Mid_up or df.loc[index, "L_Hand"][1]:
                if df.loc[index, "R_Hand"][1] > df.loc[index, "Neck"][1] or df.loc[index, "L_Hand"][1] > \
                        df.loc[index, "Neck"][1]:
                    if df.loc[index, "R_Hand"][1] > df.loc[index, "Nose"][1] or df.loc[index, "L_Hand"][1] > \
                            df.loc[index, "Nose"][1]:
                        if df.loc[index, "R_Hand"][1] > Head_TP or df.loc[index, "L_Hand"][1]:
                            H.append(5)
                        else:
                            H.append(4)
                    else:
                        H.append(3)
                else:
                    H.append(2)
            else:
                H.append(1)
        else:
            H.append(0)
    MaxHeight = max(H)
    return MaxHeight


def calc_mean_pos(hand, axis, startpos, stoppos):
    # just takes the mean of a set of positional values
    mean_pos = []

    for ax in axis:
        position = []
        for index in range(startpos, stoppos):
            position.append(hand.loc[index][ax])
        mean_pos.append(statistics.mean(position))

    return mean_pos


def calc_maxSize(df):
    # Calculates the maximum distance of each hand from its position at the beginning of the video/input

    Ldis = []
    Rdis = []

    LEo = calc_mean_pos(df['L_Hand'], [0, 1], 0, 4)  # change tuple input to 0,1,2 if using 3D
    REo = calc_mean_pos(df['R_Hand'], [0, 1], 0, 4)  # change tuple input to 0,1,2 if using 3D

    # calculates the distance of the hands, at each frame, from their starting position
    # assumes 2D data
    for index, frame in df.iterrows():
        if index > 4:
            Ldis.append(math.sqrt(((df.loc[index, "L_Hand"][0] - LEo[0]) ** 2) +
                                  ((df.loc[index, "L_Hand"][1] - LEo[1]) ** 2)))
            Rdis.append(math.sqrt(((df.loc[index, "R_Hand"][0] - REo[0]) ** 2) +
                                  ((df.loc[index, "R_Hand"][1] - REo[1]) ** 2)))

    LMax = max(Ldis)
    RMax = max(Rdis)
    return LMax, RMax


def calc_jointSize(df, LMax, RMax):
    # Calculates Size based on number of joints:
    # 2 = hand and elbow used
    # 1 = only hand movement, elbow stationary
    # 0 = no significant movement in that arm

    LdisElb = []
    RdisElb = []
    LElbMove = []
    RElbMove = []
    LSize = 0
    RSize = 0

    # is there significant movement in the hands?
    if LMax > 0.15:  # hand use
        Luse = 1
    else:
        Luse = 0
    if RMax > 0.15:
        Ruse = 1
    else:
        Ruse = 0

    # calculate elbow use
    # first get the elbow origin points
    LElbo = calc_mean_pos(df['LElb'], [0, 1], 0, 4)  # change tuple input to 0,1,2 if using 3D
    RElbo = calc_mean_pos(df['RElb'], [0, 1], 0, 4)  # change tuple input to 0,1,2 if using 3D

    # then calculate distance from starting position at each frame
    # this is used just to get the average amount distance across the whole time window
    for index, frame in df.iterrows():
        if index > 4:
            LdisElb.append(math.sqrt(((df.loc[index, "LElb"][0] - LElbo[0]) ** 2) +
                                     ((df.loc[index, "LElb"][1] - LElbo[1]) ** 2)))
            RdisElb.append(math.sqrt(((df.loc[index, "RElb"][0] - RElbo[0]) ** 2) +
                                     ((df.loc[index, "RElb"][1] - RElbo[1]) ** 2)))
    # now check if the distance is ever greater than the mean distance + 1SD
    for disval in LdisElb:
        LElbMove.append(disval > (statistics.mean(LdisElb) + statistics.stdev(LdisElb)))
    for disval in RdisElb:
        RElbMove.append(disval > (statistics.mean(RdisElb) + statistics.stdev(RdisElb)))
    # if the elbow ever moves beyond this threshold, we say there is elbow use
    if True in (M > 0 for M in LElbMove):
        LElbUse = 1
    else:
        LElbUse = 0
    if True in (M > 0 for M in RElbMove):
        RElbUse = 1
    else:
        RElbUse = 0

    # now determine the overall size
    # Right side
    if Ruse == 1 and RElbUse == 1:
        RSize = 2  # hand and elbow were moving
    elif Ruse == 1 and RElbUse == 0:
        RSize = 1  # only the lower arm
    else:
        RSize = 0  # no arm movement
    # Left side
    if Luse == 1 and LElbUse == 1:
        LSize = 2  # hand and elbow were moving
    elif Luse == 1 and LElbUse == 0:
        RSize = 1  # only the lower arm
    else:
        LSize = 0  # no arm movement

    return LSize, RSize, Luse, Ruse


def calc_submoves(df, FPS):
    # calculates the number of submovements, and gives the indices(locations) of the peaks

    RHdelta, RH_S = calculate_distance(df["R_Hand"], FPS)  # displacement (ie delta) between frames
    LHdelta, LH_S = calculate_distance(df["L_Hand"], FPS)

    R_peaks, _ = signal.find_peaks(RH_S, height=0.2, prominence=0.2, distance=5)
    L_peaks, _ = signal.find_peaks(LH_S, height=0.2, prominence=0.2, distance=5)

    Rsubs = len(R_peaks)
    Lsubs = len(L_peaks)

    return Lsubs, Rsubs, L_peaks, R_peaks


def calc_peakVel(HandArr, FPS):
    # smooths the velocity array and then takes the max value
    # takes one hand array as input

    _, VelArray = calculate_distance(HandArr, FPS)
    HandVel_Sm = smooth_moving(VelArray, 3)

    return max(HandVel_Sm)


def smooth_moving(data, degree):
    # uses a moving window to smooth an array

    triangle = np.concatenate((np.arange(degree + 1), np.arange(degree)[::-1]))  # up then down
    smoothed = []

    for i in range(degree, len(data) - degree * 2):
        point = data[i:i + len(triangle)] * triangle
        smoothed.append(np.sum(point) / np.sum(triangle))
    # Handle boundaries
    smoothed = [smoothed[0]] * int(degree + degree / 2) + smoothed
    while len(smoothed) < len(data):
        smoothed.append(smoothed[-1])
    return smoothed


def find_movepauses(velocity_array):
    # finds moments when velocity is below a particular threshold
    # returns array of indices for those moments

    Pause_ix = []
    for index, velpoint in enumerate(velocity_array):
        if velpoint < 0.1:
            Pause_ix.append(index)
    if len(Pause_ix) == 0:
        Pause_ix = 0

    return Pause_ix


def calc_holds(df, Ruse, Luse, subslocs_L, subslocs_R, FPS):
    # calculates the number of holds, time spent in a hold, and the average duration of any holds

    if Ruse == 1:

        # elbow
        _, RE_S = calculate_distance(df["RElb"], FPS)  # R elbow velocity
        GERix = find_movepauses(RE_S)
        # hand
        _, RH_S = calculate_distance(df["R_Hand"], FPS)
        GRix = find_movepauses(RH_S)
        # finger
        _, RF_S = calculate_distance(df["R_finger"], FPS)
        GFRix = find_movepauses(RF_S)

        # now find holds for the entire right side
        GR = []
        for handhold in GRix:
            for elbowhold in GERix:
                for fingerhold in GFRix:
                    if handhold == elbowhold:
                        if elbowhold == fingerhold:
                            GR.append(handhold)  # this is all holds of the entire right side

    if Luse == 1:

        # elbow
        _, LE_S = calculate_distance(df["LElb"], FPS)  # L elbow velocity
        GELix = find_movepauses(LE_S)
        # hand
        LH_S = calculate_distance(df["L_Hand"], FPS)
        GLix = find_movepauses(LH_S)
        # finger
        _, LF_S = calculate_distance(df["L_finger"], FPS)
        GFLix = find_movepauses(LF_S)

        # now find holds for the entire right side
        if GLix > 0 and GELix > 0 and GFLix > 0:
            GL = []
            for handhold in GLix:
                for elbowhold in GELix:
                    for fingerhold in GFLix:
                        if handhold == elbowhold:
                            if elbowhold == fingerhold:
                                GL.append(handhold)  # this is all holds of the entire right side

        if 'GL' in locals() and 'GR' in locals():
            # find holds involving both hands
            full_hold = []
            if Ruse == 1 and Luse == 1:
                for left_hold in GL:  # check, for each left hold,
                    for right_hold in GR:  # if there is a corresponding right hold
                        if left_hold == right_hold:
                            full_hold.append(left_hold)  # this is the time position of the hold

            # now we need to cluster them together
            if len(full_hold) > 0:
                full_hold = [9, 13, 14, 15, 19]
                hold_cluster = [[full_hold[0]]]
                clustercount = 0
                holdcount = 1
                for idx in range(1, len(full_hold)):
                    # if the next element of the full_hold list is not equal to the previous value,
                    if full_hold[idx] != hold_cluster[clustercount][holdcount - 1] + 1:
                        clustercount += 1
                        holdcount = 1
                        hold_cluster.append([full_hold[idx]])  # then start a new cluster
                    else:  # otherwise add the current hold to the current cluster
                        hold_cluster[clustercount].append(full_hold[idx])
                        holdcount += 1

                # we don't want holds occuring at the very beginning or end of an analysis segment
                # so we define these points as the first and last submovement, and remove all holds
                # outside these boundaries
                initial_move = min([subslocs_L, subslocs_R])
                final_move = max([subslocs_L, subslocs_R])

                for index in range(0, len(hold_cluster)):
                    if hold_cluster[0][0] < initial_move:
                        hold_cluster.pop(0)
                for index in range(len(hold_cluster), 0, -1):
                    if hold_cluster[len(hold_cluster)][0] > final_move:
                        hold_cluster.pop(len(hold_cluster))

                # now for the summary stats: find the total hold time
                hold_count = 0
                hold_time = 0
                hold_avg = []

                for index in range(0, len(hold_cluster)):
                    if len(hold_cluster[index]) >= 3:
                        hold_count += 1  # total number of single holds
                        hold_time += len(hold_cluster[index])  # get the number of frames
                        hold_avg.append(len(hold_cluster[index]))  # used to calculate average holdtime

                hold_time /= FPS  # divide by FPS to get actual time
                hold_avg = statistics.mean(hold_avg)

                return hold_count, hold_time, hold_avg

            else:  # if no full holds were found, return 0s
                return 0, 0, 0


def calc_volume_size(df):
    # calculates the volumetric size of the gesture, ie how much visual space was utlized by the hands
    # for 3D data, this is actual volume (ie. using z-axis), for 2D this is area, using only x and y\

    x_min = df['R_finger'][0][0]
    x_max = df['R_finger'][0][0]
    y_min = df['R_finger'][0][1]
    y_max = df['R_finger'][0][1]
    if len(df['R_finger'][0]) > 2:
        z_min = df['R_finger'][0][2]
        z_max = df['R_finger'][0][2]
    # at each frame, compare the current min and max with the previous, to ultimately find the outer values
    for frame in range(1, len(df)):
        if df['R_finger'][frame][0] < x_min:
            x_min = df['R_finger'][frame][0]
        if df['R_finger'][frame][0] > x_max:
            x_max = df['R_finger'][frame][0]
        if df['R_finger'][frame][0] < y_min:
            y_min = df['R_finger'][frame][1]
        if df['R_finger'][frame][0] > y_max:
            y_max = df['R_finger'][frame][1]
        if len(df['R_finger'][0]) > 2:
            if df['R_finger'][frame][0] < z_min:
                z_min = df['R_finger'][frame][2]
            if df['R_finger'][frame][0] > z_max:
                z_max = df['R_finger'][frame][2]

    if len(df['R_finger'][0]) > 2:
        # get range
        x_len = x_max - x_min
        y_len = y_max - y_min
        z_len = z_max - z_min
        # get volume
        vol = x_len * y_len * z_len
    else:
        x_len = x_max - x_min
        y_len = y_max - y_min
        # get area (ie volume)
        vol = x_len * y_len
    return vol


def calc_mcneillian_space(df):
    # this will call the define_mcneillian_grid function for each frame, then assign the hand to one space for each frame
    # output:
    # space_use - how many unique spaces were traversed
    # mcneillian_max - outer-most main space entered
    # mcneillian_mode - which main space was primarily used
    # 1 = Center-center
    # 2 = Center
    # 3 = Periphery
    # 4 = Extra-Periphery
    # subsections for periphery and extra periphery:
    # 1 = upper right
    # 2 = right
    # 3 = lower right
    # 4 = lower
    # 5 = lower left
    # 6 = left
    # 7 = upper left
    # 8 = upper

    hands = ['L_Hand','R_Hand']
    # compare, at each frame, each hand to the (sub)section limits, going from inner to outer, clockwise
    for hand in hands:
        Space = []

        for frame in range(len(df)):

            cc_xmin, cc_xmax, cc_ymin, cc_ymax, c_xmin, c_xmax, c_ymin, c_ymax, p_xmin, p_xmax, p_ymin, p_ymax = \
            define_mcneillian_grid(df, frame)

            if cc_xmin < df[hand][frame][0] < cc_xmax and cc_ymin < df[hand][frame][1] < cc_ymax:
                Space.append(1)
            elif c_xmin < df[hand][frame][0] < c_xmax and c_ymin < df[hand][frame][1] < c_ymax:
                Space.append(2)
            elif p_xmin < df[hand][frame][0] < p_xmax and p_ymin < df[hand][frame][1] < p_ymax:
                # if it's in the periphery, we need to also get the subsection
                # first, is it on the right side?
                if cc_xmax < df[hand][frame][0]:
                    # if so, we narrow down the y location
                    if cc_ymax < df[hand][frame][1]:
                        Space.append(31)
                    elif cc_ymin < df[hand][frame][1]:
                        Space.append(32)
                    else:
                        Space.append(33)
                elif cc_xmin < df[hand][frame][1]:
                    if c_ymax < df[hand][frame][1]:
                        Space.append(38)
                    else:
                        Space.append(34)
                else:
                    if cc_ymax < df[hand][frame][1]:
                        Space.append(37)
                    elif cc_ymin < df[hand][frame][1]:
                        Space.append(36)
                    else:
                        Space.append(35)
            else:  # if it's not periphery, it has to be extra periphery. We just need to get subsections
                if c_xmax < df[hand][frame][0]:
                    if cc_ymax < df[hand][frame][1]:
                        Space.append(41)
                    elif cc_ymin < df[hand][frame][1]:
                        Space.append(42)
                    else:
                        Space.append(43)
                elif cc_xmin < df[hand][frame][0]:
                    if c_ymax < df[hand][frame][1]:
                        Space.append(48)
                    else:
                        Space.append(44)
                else:
                    if c_ymax < df[hand][frame][1]:
                        Space.append(47)
                    elif c_ymin < df[hand][frame][1]:
                        Space.append(46)
                    else:
                        Space.append(45)
        if hand == 'L_Hand':
            Space_L = Space
        else:
            Space_R = Space

    # how many spaces used?
    space_use_L = len(set(Space_L))
    space_use_R = len(set(Space_R))
    # maximum distance (main spaces)
    if max(Space_L) > 40:
        mcneillian_maxL = 4
    elif max(Space_L) > 30:
        mcneillian_maxL = 3
    else:
        mcneillian_maxL = max(Space_L)
    if max(Space_R) > 40:
        mcneillian_maxR = 4
    elif max(Space_R) > 30:
        mcneillian_maxR = 3
    else:
        mcneillian_maxR = max(Space_R)
    # which main space was most used?
    mcneillian_modeL = get_mcneillian_mode(Space_L)
    mcneillian_modeR = get_mcneillian_mode(Space_R)

    return space_use_L, space_use_R, mcneillian_maxL, mcneillian_maxR, mcneillian_modeL, mcneillian_modeR


def get_mcneillian_mode(spaces):
    mainspace = []
    for space in spaces:
        if space > 40:
            mainspace.append(4)
        elif space > 30:
            mainspace.append(3)
        else:
            mainspace.append(space)

    mcneillian_mode = statistics.mode(mainspace)
    return mcneillian_mode

def define_mcneillian_grid(df, frame):
    # define the grid based on a single frame, output xmin,xmax, ymin, ymax for each main section
    # subsections can all be found based on these boundaries
    bodycent = df['Neck'][frame][1] - (df['Neck'][frame][1] - df['MidHip'][frame][1])/2
    face_width = (df['LEye'][frame][0] - df['REye'][frame][0])*2
    body_width = df['LHip'][frame][0] - df['RHip'][frame][0]

    # define boundaries for center-center
    cc_xmin = df['RHip'][frame][0]
    cc_xmax = df['LHip'][frame][0]
    cc_len = cc_xmax - cc_xmin
    cc_ymin = bodycent - cc_len/2
    cc_ymax = bodycent + cc_len/2

    # define boundaries for center
    c_xmin = df['RHip'][frame][0] - body_width/2
    c_xmax = df['LHip'][frame][0] + body_width/2
    c_len = c_xmax - c_xmin
    c_ymin = bodycent - c_len/2
    c_ymax = bodycent + c_len/2

    # define boundaries of periphery
    p_ymax = df['LEye'][frame][1] + (df['LEye'][frame][1] - df['Nose'][frame][1])
    p_ymin = bodycent - (p_ymax - bodycent) # make the box symmetrical around the body center
    p_xmin = c_xmin - face_width
    p_xmax = c_xmax + face_width

    return  cc_xmin, cc_xmax, cc_ymin, cc_ymax, c_xmin, c_xmax, c_ymin, c_ymax, p_xmin, p_xmax, p_ymin, p_ymax


def check_skeleton(df):
    cols = list(df)

    for joint in cols:
        plt.scatter(df[joint][0][0], df[joint][0][1])
        plt.text(df[joint][0][0], df[joint][0][1], joint)

    plt.show()


def plot_mcneillian_grid(cc_xmin, cc_xmax, cc_ymin, cc_ymax, c_xmin, c_xmax, c_ymin, c_ymax, p_xmin, p_xmax, p_ymin, p_ymax):

    plt.plot([cc_xmin, cc_xmin], [cc_ymin, cc_ymax])

    plt.plot([cc_xmax, cc_xmax], [cc_ymin, cc_ymax])

    plt.plot([cc_xmin, cc_xmax], [cc_ymin, cc_ymin])

    plt.plot([cc_xmin, cc_xmax], [cc_ymax, cc_ymax])



    plt.plot([c_xmin, c_xmin], [c_ymin, c_ymax])

    plt.plot([c_xmax, c_xmax], [c_ymin, c_ymax])

    plt.plot([c_xmin, c_xmax], [c_ymin, c_ymin])

    plt.plot([c_xmin, c_xmax], [c_ymax, c_ymax])



    plt.plot([p_xmin, p_xmin], [p_ymin, p_ymax])

    plt.plot([p_xmax, p_xmax], [p_ymin, p_ymax])

    plt.plot([p_xmin, p_xmax], [p_ymin, p_ymin])

    plt.plot([p_xmin, p_xmax], [p_ymax, p_ymax])


# make sure this is not run when imported
#if __name__ == "__main__":
#    import sys
    #df = main()