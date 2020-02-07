import argparse
import math
import posixpath
from itertools import tee
from statistics import mean

import matplotlib.pyplot as plt
import pandas as pd

"""
    The purpose of this script is to 
    A) Parse a file containing positions  
    B) Segmenting the list of positions into separate movements based on the change between the positions
    C) Saving the begin and end of every movement (separated per point) in a file 
    D) Plotting the separated movements and saving that plot on disk  
"""

""" 
    First we have to define some functions
"""


def pairwise(iterable):
    "s -> (s0,s1), (s1,s2), (s2, s3), ..."
    a, b = tee(iterable)
    next(b, None)
    return zip(a, b)


def create_velos(df):
    velos = []
    for sub in range(0, df.shape[1], 3):
        subset = df.iloc[:, sub:sub + 2]
        keyp_velo = []
        for (i1, row1), (i2, row2) in pairwise(subset.iterrows()):
            keyp_velo.append(math.sqrt((row2.iloc[0] - row1.iloc[0]) ** 2
                                       + (row2.iloc[1] - row1.iloc[1]) ** 2))

        velos.append(keyp_velo)
    return list(map(mean, zip(*velos)))


def plot(velos, title, output_p):
    """
       This function plots the segmented velocities in separate_movements, saving the plot to the output_p.
       Every segment should have a different colour from the previous one.
       TODO figure out if plots should be separated by hand, points or not at all
       :param velos: a list of averaged floats derived from op_output
       :param output_p: the path where the plot is do be saved
    """
    # TODO instead of all movements, use the separated movements list to colour every movement differently

    # plotting velocities wit pyplot and saving them
    plt.plot(velos)
    plt.xlabel("Time in frames")
    plt.ylabel("Velocity in averaged euclidian distance per frame")
    plt.title(title)
    plt.savefig(posixpath.join(output_p, '{}_vPlot.png'.format(title)), bbox_inches='tight')
    plt.clf()


def parse_args():
    """
    Parses given CMD arguments in a way that allows for error checking and messages accordingly.
    :return: the given CMD arguments, parsed and split -> ready to use
    """
    parser = argparse.ArgumentParser(description='Plots velocity graphs.')
    parser.add_argument('output_p', help='The path where plots should be saved.')
    parser.add_argument('opOutput', help='The path where OP output should be saved.')

    return parser.parse_args()


"""
    Now that all necessary functions have been defined we can start. 
"""

# getting parameters ready
args = parse_args()

# 4 command line arguments are required:
chosen_output_path = args.output_p
op_output = args.opOutput

# read in csv output of OP with pandas
body = pd.read_csv(posixpath.join(op_output, "sample.csv"), header=None)
hand_left = pd.read_csv(posixpath.join(op_output, "hand_left_sample.csv"), header=None)
hand_right = pd.read_csv(posixpath.join(op_output, "hand_right_sample.csv"), header=None)

body_velos = create_velos(body)
hand_left_velos = create_velos(hand_left)
hand_right_velos = create_velos(hand_right)

plot(body_velos, "Body_Movement", chosen_output_path)
plot(hand_left_velos, "Left_Hand_Movement", chosen_output_path)
plot(hand_right_velos, "Right_Hand_Movement", chosen_output_path)
