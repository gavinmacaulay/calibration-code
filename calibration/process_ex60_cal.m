function process_ex60_cal(rawfilenames, save_filename, ...
    es60_zero_error, start_sample, stop_sample, freq, c, alpha)

%function process_es60_cal(dfilename, save_filename, start_ping, stop_ping, ...
%    es60_zero_error, start_sample, num_samples)
% OR
% function process_es60cal(save_filename)
%    
% Code to calculate the calibration coefficients for es60 and ek60
% calibration data  
%
% dfilename is the name of a Ex60 raw file.
%
% save_filename is a filename to either save the partially processed data
% into, or the filename to retrieve such data from
%
% es60_zero_error is the ping number at which the es60 error is zero. A
% negative number will result in no correction being applied, as is
% appropriate for EK60 data.
%
% start_ping is the ping number to start at, and stop_ping is the ping
% number to load too. These allow one to select a region from the echo
% data.
%
% start_sample is the sample number it start at, and stop_sample is when 
% to stop. These need to bracket the sphere echo in the
% echogram. Try a wide range first and then narrow it down.
%
% sphere_ts is the ts of the sphere in dB. This is optional, and defaults
% to -42.4 if not given
%

% Written by G Macaulay
% $Id$

if nargin == 1
    load(rawfilenames) % not reall rawfilename, but rather save_filename
     process_data(data)
    return
end

for i = 1:length(rawfilenames)
    [h d] = readEKRaw(rawfilenames{i}, 'Frequencies', freq, 'GPS', 0, 'PingRange', [0 Inf],...
        'SampleRange', [start_sample stop_sample]);
    % merge into one dataset
    if i == 1
        header = h;
        data = d;
    elseif i > 1
       data.pings.power = [data.pings.power d.pings.power];
       data.pings.alongship = [data.pings.alongship d.pings.alongship];
       data.pings.athwartship = [data.pings.athwartship d.pings.athwartship];
    end
end

% Get Sp and Sv versions of the actual samples
disp('Loading raw file.')
calParams = readEKRaw_GetCalParms(header, data);
% and override some of the cal parameters based on CTD data
if nargin > 6
    calParams.soundvelocity = c;
    calParams.absorptioncoefficient = alpha/1000; % convert to dB/m
end
% tweak the cal params to suit our particular conditions at the time of the
% calibration
data = readEKRaw_ConvertPower(data, calParams, 3, {});
data = readEKRaw_ConvertAngles(data, calParams);

% keep the cal params around for later
data.cal.params = calParams;

% set stop_ping to the number of pings that we actually have
%stop_ping = length(data.pings.frequency) + start_ping;

% throw away lots of stuff that we don't need to save on memory...
data.pings.transducerdepth = data.pings.transducerdepth(1);
data.pings.frequency = data.pings.frequency(1);
data.pings.transmitpower = data.pings.transmitpower(1);
data.pings.pulselength = data.pings.pulselength(1);
data.pings.bandwidth = data.pings.bandwidth(1);
data.pings.sampleinterval = data.pings.sampleinterval(1);
data.pings.soundvelocity = data.pings.soundvelocity(1);
data.pings.absorptioncoefficient = data.pings.absorptioncoefficient(1);

% Calculate the correction for the ES60 triange wave error if require - it is 
% applied later. The offset values were given by the convertEk60ToCrest
% program and are found in the i files that convertEk60ToCrest 
% produces
% i = 1;
% if es60_zero_error >= 0
%     data.cal.error = es60_error([start_ping:stop_ping]-es60_zero_error)'; i=i+1;
% else
%     data.cal.error = zeros(stop_ping-start_ping+1,1);
% end

% find the index with the largest echo amplitude (hopefully
% the peak of the sphere echo) in the given bounds
warning('off','MATLAB:log:logOfZero')
%for i = 1:length(d)
    % get the user to draw in regions that contain just the echoes from
    % the sphere.
    hold off
    clf
    imagesc(data.pings.Sp)
    disp('Left mouse button picks points.')
    disp('Right mouse button picks last point.')
	hold on
	ii = 0;
	but = 1;
	
    xy = [];
    while but == 1
        [xi,yi,but] = ginput(1);
        if isempty(but) 
            break
        end
        plot(xi,yi,'wo')
        ii = ii+1;
        xy(ii,:) = [xi;yi]';
        if ii > 1
            plot(xy(ii-1:ii,1),xy(ii-1:ii,2),'w')
        end
    end
    ii = ii + 1;
    xy(ii,:) = xy(1,:); % close the polygon
    plot(xy(ii-1:ii,1),xy(ii-1:ii,2),'w')
	drawnow

    data.cal.polygon = xy;
    
    % now pick the max point for each ping from within the depths given by
    % the polygon that the user has just drawn
    num_pings = size(data.pings.Sp, 2); 
    num_samples = size(data.pings.Sp, 1);
    [X Y] = meshgrid([1:num_pings], [1:num_samples]);
    % find all points in X Y that are inside the user drawn polygon
    in = inpolygon(X, Y, xy(:,1), xy(:,2));
    % get the max and min sample (row) number and use them as bounds for
    % picking the max amplitude of the sphere echo
    data.cal.peak_pos = zeros(num_pings, 1); % pre-allocate storage
    data.cal.valid = ones(num_pings, 1); % pre-allocate storage
    for j = 1:num_pings % iterate over columns
        bounds = find(in(:,j) == 1);
        if ~isempty(bounds)
            % find the max amplitude that lies between the user drawn polygon
            [m k] = max(data.pings.Sp(min(bounds):max(bounds),j), [], 1);
            data.cal.peak_pos(j) = k + min(bounds) - 1; % -1 corrects an off by 1 error
        else
            data.cal.valid(j) = 0;
        end
    end
    data.cal.valid = logical(data.cal.valid);
%end

% show the echoes that have been choosen for the user to check, and let
% them blank out parts 
for i = 1:length(data)
    clf
    imagesc(data.pings.Sp)
    hold on
    plot(data.cal.peak_pos, 'w')
    hold off
    but = [1 1];
    zoom_limits = [1 size(data.pings.Sp, 2) 1 size(data.pings.Sp, 1)];
    disp('Left click and left click to define regions to remove')
    disp('Middle click and middle click to zoom in')
    disp('Two right clicks to zoom out')
    disp('Two spaces to exit')
    while but(1) <= 3
        [xi,yi,but] = ginput(2);
        if isempty(but)
            break
        end
        if but(1) == 1 && but(2) == 1
            sort(xi);
            limits = [floor(xi(1)) ceil(xi(2))];
            data.pings.Sp(:, limits(1):limits(2)) = -120; % a low value
            data.cal.valid(limits(1):limits(2)) = false;
            imagesc(data.pings.Sp)
            hold on
            plot(data.cal.peak_pos, 'w')
            axis(zoom_limits)
            hold off
        elseif but(1) == 2 && but(2) == 2
            zoom_limits = [min([xi(1) xi(2)]) max([xi(1) xi(2)]) ...
                min([yi(1) yi(2)]) max([yi(1) yi(2)])];
            axis(zoom_limits)
        elseif but(1) == 3 && but(2) == 3
            zoom_limits = [1 size(data.pings.Sp, 2) 1 size(data.pings.Sp, 1)];
            axis(zoom_limits)
        end
    end
end
hold off
warning('on','MATLAB:log:logOfZero')

% remove all transmits for which we didn't have suitable echoes (because
% there was no polygon around them, or they were blanked out for some
% reason).

%for i = 1:length(d)
    data.pings.Sp = double(data.pings.Sp(:, data.cal.valid));
    data.pings.Sv = double(data.pings.Sv(:, data.cal.valid));
    data.pings.alongship = double(data.pings.alongship(:, data.cal.valid));
    data.pings.athwartship = double(data.pings.athwartship(:, data.cal.valid));
    data.cal.peak_pos = data.cal.peak_pos(data.cal.valid);
%    data.cal.error = data.cal.error(data.cal.valid);
    data.cal.start_sample = start_sample;
%end

save(save_filename, 'data')
process_data(data)



%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
function process_data(data)

angle_factor = (data.config.anglesensitivityalongship + data.config.anglesensitivityathwartship)/2; % [dB]
sample_interval = double(data.cal.params.soundvelocity * data.pings.sampleinterval * 0.5); % [m]
ba_db = data.config.equivalentbeamangle; % [dB re 1 steradian], two-way equivalent beam angle
sphere_ts = getSphereTS(data.pings.frequency);

% Pick out the peak amplitudes for use later on, and discard the
% rest. For Sv keep the 9 samples that surround the peak too.

pp = data.cal.peak_pos;
tts = zeros(size(pp));
ssa = zeros(length(pp),9);
range = zeros(length(pp),1);
for j = 1:length(pp)
    tts(j) = data.pings.Sp(pp(j), j);
    along(j) = data.pings.alongship(pp(j), j);
    athwart(j) = data.pings.athwartship(pp(j), j);
    ssv(j,:) = data.pings.Sv(pp(j)-4:pp(j)+4, j);
    range(j) = pp(j);
end
data.cal.ts = tts;
data.cal.sv = ssv;
% Make the range the range from the transducer in metres
data.cal.range = (range + data.cal.start_sample) * sample_interval;

along = along';
athwart = athwart';
clear tts ssa range pp

amp_ts = data.cal.ts;
amp_sv = data.cal.sv';

%error = 10.^(error/20); % convert from dB to a ratio
     
% Correct for es60 triangle wave error
%amp_ts = amp_ts ./ error;
%amp_sa = amp_sa ./ repmat(error',9,1);

%amp_sa = amp_sa / system_calibration_sa;
%amp_sa = amp_sa.^2;

sphere = [amp_ts athwart along data.cal.range];

% the amp_ts and range data are now in sphere
clear amp_ts range

% Remove any echoes that are grossly wrong. Do this by assuming a beamwidth
% and removing all echoes more than maxdBDiff dB off
faBW = data.config.beamwidthalongship; % [degrees]
psBW = data.config.beamwidthathwartship; % [degrees]

% trim echoes to those within about 0.7 times the beamwidth
% This is not exact because we haven't yet calculated the
% beam centre offsets, but it will do for the moment
trimTo = 0.7 * mean([faBW psBW]) * 0.5;
i = find(abs(sphere(:,2)) < trimTo & abs(sphere(:,3)) < trimTo);
sphere = sphere(i,:);
amp_sv = amp_sv(:,i);

% Use the Simrad theoretical beampattern formula
theoreticalTS = sphere_ts - 6 * ((2*sphere(:,3)/faBW).^2 + (2*sphere(:,2)/psBW).^2) .^ 1.1;
diffTS = theoreticalTS - sphere(:,1);
maxdBDiff = 6; % any point more than maxDbDiff from the theoretical will be discarded as outliers
i = find(abs(diffTS) <= maxbDDiff);
sphere = sphere(i,:);
amp_sv = amp_sv(:,i);

% estimate the beam widths and centres
% do the alongship beamwidth by selecting just those
% echoes within 2% of the athwartship beamangle
i_fa = find(abs(sphere(:,2)) < 0.02 * psBW);
try
    [offset_fa faBW p_coeffs_fa pts_used_fa peak_fa] = fit_beampattern(sphere(i_fa,1), sphere(i_fa,3), 1.0, 4, faBW);
catch
    disp('Using a polynomial order of 2 instead of 4. for alongship beamwidth.')
    [offset_fa faBW p_coeffs_fa pts_used_fa peak_fa] = fit_beampattern(sphere(i_fa,1), sphere(i_fa,3), 1.0, 2, faBW);
end

% do the athwartship beamwidth
i_ps = find(abs(sphere(:,3)) < 0.02 * faBW);
try
    [offset_ps psBW p_coeffs_ps pts_used_ps peak_ps] = fit_beampattern(sphere(i_ps,1), sphere(i_ps,2), 1.0, 4, psBW);
catch
    [offset_ps psBW p_coeffs_ps pts_used_ps peak_ps] = fit_beampattern(sphere(i_ps,1), sphere(i_ps,2), 1.0, 2, psBW);
    disp('Using a polynomial order of 2 instead of 4 for athwartship beamwitdh.')
end
mean_peak = mean([peak_fa peak_ps]);

% apply the offsets to the angles
sphere(:,2) = sphere(:,2) - offset_ps;
sphere(:,3) = sphere(:,3) - offset_fa;

% convert to conical angle
t1 = tan(deg2rad(sphere(:,2)));
t2 = tan(deg2rad(sphere(:,3)));
phi = rad2deg(atan(sqrt(t1.*t1 + t2.*t2)));
theta = rad2deg(atan2(t1, t2));

% compensate for position in beam
compensation = simradBeamCompensation(faBW, psBW, sphere(:,3), sphere(:,2));

% do a plot to show the beam pattern
clf
[XI YI]=meshgrid([-trimTo:.1:trimTo],[-trimTo:.1:trimTo]);
warning('off','MATLAB:griddata:DuplicateDataPoints');
ZI=griddata(sphere(:,2), sphere(:,3), sphere(:,1),XI,YI);
warning('on','MATLAB:griddata:DuplicateDataPoints');
contourf(XI,YI,ZI)
disp('This figure is for a visual quality check of the beam pattern')
axis equal
grid
xlabel('port/starboard angle')
ylabel('fore/aft angle')
colorbar
disp('Press a key to add the sphere positions to this figure')
pause
% and plot the positions of the sphere. note that there is a bug in matlab
% where the point (.) marker doesn't have a continuous size range, so we
% used a marker that does (the available . sizes are not right).
hold on
plot(sphere(:,2), sphere(:,3),'+','MarkerSize',2,'MarkerEdgeColor',[.5 .5 .5])
disp('Press any key to continue')
pause

clf
plot(sphere(:,4))
disp(['Mean sphere range = ' num2str(mean(sphere(:,4))) ...
    ' m, std = ' num2str(std(sphere(:,4))) ' m.'])
disp('The sphere range during the calibration. Press any key to continue')
pause

% do a plot for a calibration report
clf
subplot(2,1,1)
ss = sphere(i_fa,:);
comp_fa = compensation(i_fa);
plot(ss(pts_used_fa,3), ss(pts_used_fa,1), '.')
hold on
plot(ss(pts_used_fa,3), ss(pts_used_fa,1)+comp_fa(pts_used_fa), 'r.')
x = [-trimTo:.1:trimTo];
beam = simradBeamCompensation(faBW, psBW, x, 0);
plot(x, mean_peak-beam, 'k')
xlabel('Fore/aft angle (\circ)')
ylabel('Sphere target strength (dB re 1m^2)')

subplot(2,1,2)
ss = sphere(i_ps,:);
comp_ps = compensation(i_ps);
plot(ss(pts_used_ps,2), ss(pts_used_ps,1), '.')
hold on
plot(ss(pts_used_ps,2), ss(pts_used_ps,1)+comp_ps(pts_used_ps), 'r.')
beam = simradBeamCompensation(faBW, psBW, 0, x);
plot(x, mean_peak-beam, 'k')
xlabel('Port/starboard angle (\circ)')
ylabel('Sphere target strength (dB re 1m^2)')
disp('This figure is intended for including in the calibration report')
disp('Press any key to continue')
pause

% and do some filtering of outliers based on
% the corrected data
i = find(sphere(:,1)+compensation <= -40 & sphere(:,1)+compensation > -48);
sphere = sphere(i,:);
compensation = compensation(i);
phi = phi(i);
theta = theta(i);
amp_sv = amp_sv(:,i);

% calc the on-axis value and compare to the expected TS
% from the calibration sphere
on_axis = 0.015 * mean(faBW + psBW); % echoes within 1.5% of the beamangle are taken to be on axis. 
i = find(phi < on_axis);
use_corrected = 0;
if isempty(i)
    % use corrected echoes within 1on_axis * 10 deg of centre instead
    i = find(phi < on_axis*10);
    use_corrected = 1;
end

if use_corrected == 0
    mean_ts = 20*log10(mean(10.^(sphere(i,1)/20)));
else
    mean_ts = 20*log10(mean(10.^((sphere(i,1)+compensation(i))/20)));
end
% Filter again a bit more closely now that we know the target sphere echo
% strength as seen by the sounder
i = find(sphere(:,1)+compensation <= mean_ts+2 & sphere(:,1)+compensation > mean_ts-2);
sphere = sphere(i,:);
compensation = compensation(i);
phi = phi(i);
theta = theta(i);
amp_sv = amp_sv(:,i);

% and recalculate the mean_ts
if use_corrected == 0
    i = find(phi < on_axis);
    mean_ts = 20*log10(mean(10.^(sphere(i,1)/20)));
    std_ts = std(sphere(i,1));
else
    i = find(phi < on_axis*10);
    mean_ts = 20*log10(mean(10.^((sphere(i,1)+compensation(i))/20)));
    std_ts = std(sphere(i,1)+compensation(i));
end

if use_corrected == 0
    oa = num2str(on_axis);
else
    oa = num2str(on_axis*10);
end
disp(['Mean ts within ' oa ' deg of centre = ' num2str(mean_ts) ' dB'])
disp(['Std of ts within ' oa ' deg of centre = ' num2str(std_ts) ' dB'])
disp(['Number of echoes within ' oa ' deg of centre = ' num2str(length(sphere(i,1)))])

outby = sphere_ts - mean_ts;
if outby > 0
    disp(['Ex60 is reading ' num2str(outby) ' dB too low'])
else
    disp(['Ex60 is reading ' num2str(abs(outby)) ' dB too high'])
end

disp(['Add ' num2str(-outby/2) ' dB to G_o'])

% plot the data and results
clf
plot(phi, sphere(:,1), '.')
xlabel('Angle off axis (degrees)')
ylabel('Sphere target strength')
hold on
plot(phi, sphere(:,1)+compensation, 'r.')
xlabel('Angle off axis (degrees)')

disp(['Fore/aft beamwidth = ' num2str(faBW) ' degrees'])
disp(['Fore/aft offset = ' num2str(offset_fa) ' degrees'])
disp(['Port/stbd beamwidth = ' num2str(psBW) ' degrees'])
disp(['Port/stbd offset = ' num2str(offset_ps) ' degrees'])

disp(['Results obtained from ' num2str(length(sphere(:,1))) ' sphere echoes'])
disp(['Using c = ' num2str(data.cal.params.soundvelocity) ' m/s'])
disp(['Using alpha = ' num2str(data.cal.params.absorptioncoefficient*1000) ' dB/km'])
% Calculate the sa correction value

% also calculate the integral of the sphere echoes
r_0 = 1; % [m], reference range

ba = 10^(ba_db/10); % two-way equivalent beam angle, linear units
sigma_bs = 10^(sphere_ts/10); % [m^2], sphere ts 
sa_theory = (4*pi*r_0^2*sigma_bs./(ba * sphere(:,4).^2))';

% for sa, just use echoes within on_axis degrees of the beam centre
sa_measured = 4*pi*r_0^2 * sum(10.^(amp_sv(:,i)/10)) * sample_interval;

sa_correction = sa_measured ./ sa_theory(i);
sa_correction = 10*log10(mean(sa_correction)) / 2;
% correct for incorrect G_0
sa_correction = sa_correction + outby/2;

disp(['sa correction = ' num2str(sa_correction) ' dB'])

% calculate the RMS fit to the beam model
fit_out_to = mean(psBW + faBW) * 0.5; % fit out to half the beamangle
i = find(phi <= fit_out_to);
beam_model = mean_ts - compensation;
rms_fit = sqrt(mean((sphere(i,1) - beam_model(i)).^2));
disp(['RMS of fit to beam model out to ' num2str(fit_out_to) ' degrees = ' num2str(rms_fit) ' dB'])

disp(['Using a sphere ts of ' num2str(sphere_ts) ' dB'])

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

function [offset, bw, p, pts_used, peak] = fit_beampattern(ts, echoangle, ...
    limit, polyorder, bw)
% A function to estimate the beamwidth from the given data. ts andechoangle
% should be the same size and contain the target strength and respective
% angles. TS points more than limit dB away from the polynomial fit are
% discarded and a new polynomial fit calculated.

% derive the bounds on the search for a zero-crossing based on the
% transducer beamwidth
minAngle = 0.3 * bw/2;
maxAngle = 1.5 * bw/2;

p = polyfit(echoangle, ts, polyorder);
shape = @(x) -polyval(p, x);
offset = fminsearch(shape, 0);
peak = polyval(p, offset);
% now find the 3 dB points and calculate the beamwidth
shape = @(x) polyval(p, x) - peak + 20*log10(2);
bw = fzero(shape, [minAngle maxAngle]) - fzero(shape, [-maxAngle -minAngle]);

% idenitfy and ignore points that are too far from the polynomial and
% recalculate the polynomial.
ii = find(abs(ts - polyval(p, echoangle)) <= limit);
p = polyfit(echoangle(ii), ts(ii), polyorder);
shape = @(x) -polyval(p, x);
offset = fminsearch(shape, 0);
peak = polyval(p, offset);
% now find the 3 dB points and calculate the beamwidth
shape = @(x) polyval(p, x) - peak + 20*log10(2);
bw = fzero(shape, [minAngle maxAngle]) - fzero(shape, [-maxAngle -minAngle]);
pts_used = ii;

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
function compensation = simradBeamCompensation(faBW, psBW, faAngle, psAngle)
% Calculates the simard beam compensation given the beam angles and
% positions in the beam

part1 = 2*faAngle/faBW;
part2 = 2*psAngle/psBW;
compensation = 6 * (part1.^2 + part2.^2) .^ 1.1;
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Get a nominal ts for the given frequency
function sphere_ts = getSphereTS(freq)

switch freq
    case 18000
        sphere_ts = -42.7;
    case 38000
        sphere_ts = -42.3;
    case 70000
        sphere_ts = -41.1;
    case 120000
        sphere_ts = -40.0;
    case 200000
        sphere_ts = -39.9;
    otherwise
        disp(['Sphere TS not known for a freq of ' num2str(freq) ' Hz'])
end
