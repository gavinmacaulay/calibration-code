%%
% Ad-hoc code to produce bulk sphere TS estimates. Just alter the section
% surrounded by %%%%%%% and for a different sphere material, the values in the 
% para struct
freq_range = [0.01 473.7720];
scale = 1;
n = 4000;
target_index = 1;
proc_flag = 1;

T = [];
P = [];
S = [];
cw = [1450:5:1520]; % water sound speed [m/s]
rhow = 1025.3288; % water density [kg/m^3]

%%%%%%%
freq_spec = 333; % TS at this freq [kHz]
ave_BW = 27.944; % bandwidth to average over [kHz]
D = 10; % sphere diameter [mm]
%%%%%%%

disp(['For c = ' num2str(cw) ' m/s'])
for i = 1:length(cw)
    % for WC
    para = struct('rho', 14900, 'cc', 6853, 'cs', 4171, 'ave_value', ave_BW, ...
        'ave_unit', 0, 'n', n, 'out_flag', 2, 'a', D/2/1000, 'cw', cw(i), ...
        'rhow', rhow, 'freq_range', freq_range, 'freq_spec', freq_spec, ...
        'ave_BW', ave_BW, 'scale', scale, 'target_index', target_index, ...
        'proc_flag', proc_flag, 'D', D, 'T', T, 'P', P, 'S', S);

    [para,out]=solid_elastic_sphere_TS_fun(freq_range,freq_spec,scale,n,target_index,proc_flag,D,T,P,S,cw(i),rhow,ave_BW,para);
    disp([num2str(out.TS_spec_ave,'%.2f')])
end