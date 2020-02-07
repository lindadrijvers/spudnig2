import argparse
import os
import posixpath
import subprocess
import cv2
import re
import time
import atexit

import sort_openpose_output
import movements2


def format_path(any_path):
    chars = []
    for char in any_path:
        if char == '\\':
            chars.append('/')
        else:
            chars.append(char)
    return ''.join(chars)


def parent(any_path, times=1):
    for i in range(0, times):
        any_path = os.path.dirname(any_path)
    return any_path


def create_data(args, openpose):
    """Starts the analysis of the video when analyze button is clicked"""
    try:
        os.mkdir(args.temp_dir)
    except FileExistsError:
        print("Output folder already exists, not created")

    model = posixpath.join(parent(openpose, 2), "models")
    openpose_run_command = openpose + ' ' + ' '.join(
        ["--video " + args.filename, "-num_gpu -1", "--hand", "--write_json",
         args.temp_dir, "--model_pose BODY_25", "--net_resolution -1x144", "number_people_max 1",
         "--display 0", "--render_pose 0", "--model_folder " + model, "-cli_verbose 1"])

    try:
        op = subprocess.Popen(openpose_run_command, stdout=subprocess.PIPE)
        global child_threads
        child_threads.append(op)
        while op.poll() is None:
            time.sleep(0.25)
            output = op.stdout.readline()

            if not output:
                break
            output = output.decode("utf-8")
            if is_progress(output):
                print(extract_progress(output.strip()), flush=True)
        op.wait()
    except Exception as e:
        print(e, flush=True)


def analysis(args, keypoints_left, keypoints_right, keypoints_body, fps, min_cutoff, gap_cutoff):
    sort_openpose_output.sort_openpose(args.temp_dir, keypoints_left, keypoints_right, keypoints_body)

    return movements2.main(args.temp_dir, args.threshold, keypoints_left, keypoints_right, keypoints_body, fps,
                           min_cutoff, gap_cutoff)


def is_progress(output):
    return re.search("^Processing frame ", output) is not None


def extract_progress(output):
    return output.split()[-1][:-3]


def save_file(data, savefile, output_folder, filetype):
    """Saves the Elan importable file on a location selected by the user."""

    # TODO Don't write to savefile but to another file and then convert.
    if not savefile.endswith(filetype):
        savefile = savefile[0:len(savefile) - 4] + filetype
    data.to_csv(savefile, header=False)
    csv_file = open(savefile)
    print(csv_file)

    # TODO convert to json

    # data.to_json(savefile)
    # reader = csv.DictReader(csvfile, fieldnames=("idk yet"))
    # json_output = json.dumps([row for row in reader])
    # json_file = open(savefile, 'w')
    # json_file.write(json_output)

    # TODO convert to eaf
    # shutil.rmtree(output_folder, True) TODO this should be removed, right?
    # try:
    #     os.remove('hand_left_sample.csv')
    #     os.remove('hand_right_sample.csv')
    #     os.remove('sample.csv')
    # except OSError as e:
    #     print("File cannot be removed.", flush=True)
    #     print(e, flush=True)


def parse_args():
    parser = argparse.ArgumentParser(description='Run spudnig.')
    parser.add_argument('--gpu', dest='gpu', default=False, action='store_true',
                        help='Whether the GPU should be used instead of the CPU.')
    parser.add_argument('threshold', metavar='threshold', type=float,
                        help='Reliability threshold.')
    parser.add_argument('min_cutoff', type=int, help='Minimal number of frames over which a movement should be '
                                                     'detected to be interpreted as a movement')
    parser.add_argument('gap_cutoff', type=int, help='Minimal number of frames between 2 movements, otherwise they '
                                                     'are merged into 1')
    parser.add_argument('filename', metavar='fn', help='File to be processed.')
    parser.add_argument('temp_dir', help='Directory to be used for temporary files')
    parser.add_argument('savefolder', metavar='sf', help='Folder where the results should be in.')
    parser.add_argument('-kpl', dest='keypoints_left', help='List of left-hand keypoints under consideration')
    parser.add_argument('-kpr', dest='keypoints_right', help='List of right-hand keypoints under consideration')
    parser.add_argument('-kpb', dest='keypoints_body', help='List of body keypoints under consideration')
    parser.add_argument('filetype', choices=['.csv', '.json', '.eaf'], default='csv', action='store',
                        help='File type of the file the result is written to.')
    return parser.parse_args()


def get_fps(video):
    capture = cv2.VideoCapture(video)

    if int(cv2.getVersionMajor()) < 3:
        fps = capture.get(cv2.cv.CV_CAP_PROP_FPS)
    else:
        fps = capture.get(cv2.CAP_PROP_FPS)
    capture.release()
    return fps


def keypoint_check(arg_list):
    if arg_list == '[]':
        return []
    else:
        return list(map(int, arg_list[1:-1].split(",")))


def init():
    os.chdir(parent(os.path.realpath(__file__)))
    args = parse_args()
    keypoints_left = keypoint_check(args.keypoints_left)
    keypoints_right = keypoint_check(args.keypoints_right)
    keypoints_body = keypoint_check(args.keypoints_body)
    wd = format_path(os.getcwd())
    openpose = posixpath.join(parent(wd, 2), "openpose_cpu", "bin", "OpenPoseDemo.exe")
    fps = get_fps(args.filename)
    return args, openpose, fps, keypoints_left, keypoints_right, keypoints_body


def main():
    args, openpose, fps, keypoints_left, keypoints_right, keypoints_body = init()
    create_data(args, openpose)
    data = analysis(args, keypoints_left, keypoints_right, keypoints_body, fps, args.min_cutoff,
                    args.gap_cutoff)
    savefile = posixpath.join(args.savefolder, os.path.split(args.filename)[-1])
    save_file(data, savefile, args.temp_dir, args.filetype)


def kill_child_threads():
    for p in child_threads:
        if p.poll() is None:
            p.kill()


if __name__ == '__main__':
    child_threads = []
    atexit.register(kill_child_threads)
    main()
