%%
% Ad-hoc code to produce bulk sphere TS estimates. Just alter the section
% surrounded by %%%%%%%.
% Will generate a table for all combinations of the given sound speed and
% averaging bandwidth.

%%%%%%%
cw = 1450:5:1520; % water sound speed [m/s]
rhow = 1025.3288; % water density [kg/m^3]
% WC
rhos = 14947; % density of sphere [m/s]
cc = 6853; % compressional sound speed in sphere [m/s]
cs = 4171; % shear sound speed in sphere [m/s]
% Cu
%rhos = 8947: % density of sphere [m/s]
%cc = 4760; % compressional sound speed in sphere [m/s]
%cs = 2288.5; % shear sound speed in sphere [m/s]

freq_spec = 38; % TS at this freq [kHz]
ave_BW = [3.675 3.275 2.425 1.448 0.766]; % bandwidths to average over [kHz]
D = 38.1; % sphere diameter [mm]
%%%%%%%

freq_range = [0.01 473.7720];
scale = 1;
n = 4000;
target_index = 1;
proc_flag = 1;

T = [];
P = [];
S = [];


disp(['c\bw ' num2str(ave_BW, '%.3f ')])
for i = 1:length(cw)
    r = [];
    for j = 1:length(ave_BW)
        para = struct('rho', rhos, 'cc', cc, 'cs', cs, 'ave_value', ave_BW(j), ...
            'ave_unit', 0, 'n', n, 'out_flag', 2, 'a', D/2/1000, 'cw', cw(i), ...
            'rhow', rhow, 'freq_range', freq_range, 'freq_spec', freq_spec, ...
            'ave_BW', ave_BW(j), 'scale', scale, 'target_index', target_index, ...
            'proc_flag', proc_flag, 'D', D, 'T', T, 'P', P, 'S', S);

        [para,out]=solid_elastic_sphere_TS_fun(freq_range,freq_spec,scale,n,target_index,proc_flag,D,T,P,S,cw(i),rhow,ave_BW,para);
        r = [r out.TS_spec_ave];
    end
    disp([num2str(cw(i)) ' ' num2str(r,'%.2f ')])
end