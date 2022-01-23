import time
from src.RemoteControl import RemoteControl
from concurrent.futures import ThreadPoolExecutor

HOST = '192.168.43.1'  # The smartphone's IP address


def main():
    remote = RemoteControl(HOST)

    with ThreadPoolExecutor(max_workers=1) as executor:
        future = executor.submit(remote.get_imu, 10000, True, True, False)
        # Do something else
        print("doing other stuff...")
        time.sleep(10)
        print("done doing other stuff")
        # Get result when needed
        accel_data, gyro_data, _ = future.result()
        # Process result somehow (here just file output)
        print("GYRO data length: %d" % len(gyro_data))
        with open("gyro.csv", "w+") as gyro:
            gyro.writelines(gyro_data)

    print('EXITED')


    phase, duration, exp_time = remote.start_video()
    print("%d %f" % (phase, duration))
    time.sleep(5)
    remote.stop_video()

    remote.close()


if __name__ == '__main__':
    main()

