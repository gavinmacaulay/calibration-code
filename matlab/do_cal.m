%% 
% An example of a Matlab script to do a calibration

filename = {'ES70-38kHz-D20100429-T223949.raw'}; % change this to suit

% An ES60 or ES70 with the a triangle wave error correction
e = estimate_es60_zero_error_ping(filename, 38000);

% Do the calibration. If necessary, calculate the water properties for input to the mfile.
process_ex60_cal(filename, 'ES70-38kHz-cal', e, 20, 30, 38000, 1470, 10.3, -42.4, 1)

% Once the above mfile has been run, the processing (without the manual echo selection part) can be easily rerun.
process_ex60_cal('ES70-38kHz-cal')

