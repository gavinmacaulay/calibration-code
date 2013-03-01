%%
% Ad-hoc code to produce bulk sphere TS estimates. Just alter the section
% surrounded by %%%%%%%.
% Will generate a table for all combinations of the given sound speed and
% averaging bandwidth.

%%%%%%%
cw = 1450:5:1520; % water sound speed [m/s]
rhow = 1025.3288; % water density [kg/m^3]
%rhow = 1027
% WC
rhos = 14947; % density of sphere [m/s]
cc = 6853; % compressional sound speed in sphere [m/s]
cs = 4171; % shear sound speed in sphere [m/s]
% Cu
%rhos = 8947: % density of sphere [m/s]
%cc = 4760; % compressional sound speed in sphere [m/s]
%cs = 2288.5; % shear sound speed in sphere [m/s]

% % For Simrad EK60. Bandwidths to average over [kHz]
%spec = struct('freq', 18, 'BW', [1.73 1.56 1.17 0.71 0.38]); % for 18 kHz pulse lengths (512, 1024, 2048, 4096, 8192)
%spec = struct('freq', 38, 'BW', [3.675 3.275 2.425 1.448 0.766]); % for 38 kHz pulse lengths (256, 512, 1024, 2048, 4096)
%spec = struct('freq', 70, 'BW', [6.74 6.09 4.63 2.83 1.51]); % for 70 kHz pulse lengths (128, 256, 512, 1024, 2048)
spec = struct('freq', 120, 'BW', [11.66 10.79 8.61 5.49 2.99]); % for 120 kHz pulse lengths (64, 128, 256, 512, 1024)
%spec = struct('freq', 200, 'BW', 18.54 15.55 10.51 5.90 3.05]); % for 200 kHz pulse lengths (64 128 256 512 1024)
%spec = struct('freq', 333, 'BW', [64 128 256 512 1024]); % for 333 kHz pulse lengths (64 128 256 512 1024)

D = 22; % sphere diameter [mm]
%%%%%%%

freq_range = [0.01 473.7720];
scale = 1;
n = 4000;
target_index = 1;
proc_flag = 1;

T = [];
P = [];
S = [];

disp(['For a ' num2str(D) ' mm diameter sphere.'])
disp(['Sphere density = ' num2str(rhos) ' kg/m^3'])
disp(['Sphere compressional sound speed = ' num2str(cc) ' m/s'])
disp(['Sphere shear sound speed = ' num2str(cs) ' m/s'])

disp(['Water density = ' num2str(rhow) ' kg/m^3'])
disp(' ')

disp(['TS at ' num2str(spec.freq) ' kHz'])
disp(' ')

disp(['c\bw ' num2str(spec.BW, '%.3f ')])
for i = 1:length(cw)
    r = [];
    for j = 1:length(spec.BW)
        para = struct('rho', rhos, 'cc', cc, 'cs', cs, 'ave_value', spec.BW(j), 'ave_unit', 0);

        [para,out]=solid_elastic_sphere_TS_fun(freq_range,spec.freq,scale,n,target_index,proc_flag,D,T,P,S,cw(i),rhow,para);

        r = [r out.TS_spec_ave];
    end
    disp([num2str(cw(i)) ' ' num2str(r,'%.2f ')])
end
