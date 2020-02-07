# -*- coding: utf-8 -*-
"""
Created on Tue Jun  4 11:11:10 2019

@author: jorrip
"""
import posixpath
import json
import sys

import pandas as pd
import os


def keypoint_check(arg_list, range_factor):
    if arg_list == '[]':
        return []
    else:
        return [i for i in range(range_factor) if int(i / 3) in arg_list]


def find_first_nonempty_file(root):
    for _, _, files in os.walk(root):
        for file in files:
            with open(posixpath.join(root, file), "r") as read_file:
                data = json.load(read_file)
                if data['people']:
                    return data


def sort_openpose(root, keypoints_left, keypoints_right, keypoints_body):
    """Converts the OpenPose output to CSV files for the movement analyzer."""

    hand_left = []
    hand_right = []
    pose = []
    keypoints_left = keypoint_check(keypoints_left, 63)
    keypoints_right = keypoint_check(keypoints_right, 63)
    keypoints_body = keypoint_check(keypoints_body, 78)

    for _, _, files in os.walk(root):
        prev_data = None
        for file in files:
            with open(posixpath.join(root, file), "r") as read_file:
                data = json.load(read_file)
                if not data['people']:
                    if prev_data is None:
                        data = find_first_nonempty_file(root)
                    else:
                        data = prev_data
                prev_data = data


            previousDataLeft = data['people'][0]['hand_left_keypoints_2d']
            previousDataRight = data['people'][0]['hand_right_keypoints_2d']
            previousDataBody = data['people'][0]['pose_keypoints_2d']

            try:
                data['people'][0]['hand_left_keypoints_2d'] = [data['people'][0]['hand_left_keypoints_2d'][f] for f
                                                               in keypoints_left]
                hand_left.append(data['people'][0]['hand_left_keypoints_2d'])
            except:
                print("while the data of a frame to the data, an error occurred", flush=True)
                print("The progress was: " + str(files.index(file)) + " of " + str(len(files)), flush=True)
                print("KPL: " + str([data['people'][0]['hand_left_keypoints_2d']]), flush=True)
                hand_left.append(previousDataLeft)
            try:
                data['people'][0]['hand_right_keypoints_2d'] = [data['people'][0]['hand_right_keypoints_2d'][f] for
                                                                f in keypoints_right]
                hand_right.append(data['people'][0]['hand_right_keypoints_2d'])
            except:
                print("while the data of a frame to the data, an error occurred", flush=True)
                print("The progress was: " + str(files.index(file)) + " of " + str(len(files)), flush=True)
                print("KPR: " + str([data['people'][0]['hand_right_keypoints_2d']]), flush=True)
                hand_right.append(previousDataRight)
            try:
                data['people'][0]['pose_keypoints_2d'] = [data['people'][0]['pose_keypoints_2d'][f] for
                                                          f in keypoints_body]
                pose.append(data['people'][0]['pose_keypoints_2d'])
            except:
                print("while the data of a frame to the data, an error occurred", flush=True)
                print("The progress was: " + str(files.index(file)) + " of " + str(len(files)), flush=True)
                print("KPB: " + str([data['people'][0]['pose_keypoints_2d']]), flush=True)
                pose.append(previousDataBody)

    pose_csv = pd.DataFrame(pose)
    hand_left_csv = pd.DataFrame(hand_left)
    hand_right_csv = pd.DataFrame(hand_right)

    hand_left_csv.to_csv(posixpath.join(root, 'hand_left_sample.csv'), encoding='utf-8', index=False, header=None)
    hand_right_csv.to_csv(posixpath.join(root, 'hand_right_sample.csv'), encoding='utf-8', index=False, header=None)
    pose_csv.to_csv(posixpath.join(root, 'sample.csv'), encoding='utf-8', index=False, header=None)
