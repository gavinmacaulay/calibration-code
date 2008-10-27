function process_ex60_cal(rawfilenames, save_filename, ...
    es60_zero_error, start_depth, stop_depth, freq, c, alpha, sphere_ts)

%function process_es60_cal(dfilenames, save_filename, es60_zero_error, ...
%    start_depth, stop_depth, freq, c, alpha, sphere_ts)
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
% es60_zero_error is an array of ping numbers at which the es60 error is
% zero. There should be one number for each raw filename given. A negative
% number will result in no correction being applied, as is appropriate for
% EK60 data.
%
% start_depth is the depth [m] it start at, and stop_depth is when 
% to stop. These need to bracket the sphere echo in the
% echogram. Try a wide range first and then narrow it down.
%
% freq is the sounder freq in Hz
%
% c is the sound speed in m/s
%
% sphere_ts is the ts of the sphere in dB. This is optional, and defaults
% to a frequency specific value if not given
%

% Written by G Macaulay
% $Id$

scc_revision = '$Revision$';
% pick out just the number
scc_revision = regexprep(scc_revision, '[^\d]', '');

if nargin == 1
    load(rawfilenames) % not reall rawfilename, but rather save_filename
    process_data(data, scc_revision)
    return
end

for i = 1:length(rawfilenames)
    
    % read in the first ping to get the parameters that are needed to
    % convert the given depth ranges into sample range.
    [h d] = readEKRaw(rawfilenames{i}, 'Frequencies', freq, 'GPS', 0, 'PingRange', [1 1], ...
        'SampleRange', [1 1]);
    start_sample = round(2* start_depth / (d.pings(1).sampleinterval * d.pings(1).soundvelocity));
    stop_sample = round(2* stop_depth / (d.pings(1).sampleinterval * d.pings(1).soundvelocity));
    
    % and then read in the data for real
    [h d] = readEKRaw(rawfilenames{i}, 'Frequencies', freq, 'GPS', 0, 'PingRange', [1 Inf],...
        'SampleRange', [start_sample stop_sample]);

    % Calculate the correction for the ES60 triange wave error if required - it is
    % applied later.
    d.cal.es60_zero_error = es60_zero_error;
    
    num_pings = size(d.pings.power, 2);
    if es60_zero_error(i) >= 0
        d.pings.es60_error = es60_error((1:num_pings)-es60_zero_error(i));
    else
        d.pings.es60_error = zeros(1, num_pings);
    end

    % merge into one dataset
    if i == 1
        header = h;
        data = d;
    elseif i > 1
       data.pings.power = [data.pings.power d.pings.power];
       data.pings.alongship = [data.pings.alongship d.pings.alongship];
       data.pings.athwartship = [data.pings.athwartship d.pings.athwartship];
       data.pings.es60_error = [data.pings.es60_error d.pings.es60_error];
    end
end

% keep for records.
data.cal.es60_error = es60_zero_error;

% Work out the sphere ts if it hasn't been given
if nargin == 8
    sphere_ts = getSphereTS(data.pings.frequency);
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
data = readEKRaw_ConvertPower(data, calParams, 3, {'KeepPower', true});
data = readEKRaw_ConvertAngles(data, calParams);

% keep the cal params around for later
data.cal.params = calParams;

% throw away lots of stuff that we don't need to save on memory...
data.pings.transducerdepth = data.pings.transducerdepth(1);
data.pings.frequency = data.pings.frequency(1);
data.pings.transmitpower = data.pings.transmitpower(1);
data.pings.pulselength = data.pings.pulselength(1);
data.pings.bandwidth = data.pings.bandwidth(1);
data.pings.sampleinterval = data.pings.sampleinterval(1);
data.pings.soundvelocity = data.pings.soundvelocity(1);
data.pings.absorptioncoefficient = data.pings.absorptioncoefficient(1);

% find the index with the largest echo amplitude (hopefully
% the peak of the sphere echo) in the given bounds
warning('off','MATLAB:log:logOfZero')

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
    xy(ii,:) = [xi;yi]'; %#ok<AGROW>
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
[X Y] = meshgrid(1:num_pings, 1:num_samples);
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

% show the echoes that have been choosen for the user to check, and let
% them blank out parts
originalSp = data.pings.Sp; % So we can restore/undo deleted regions
for i = 1:length(data)
    clf
    imagesc(data.pings.Sp)
    hold on
    plot(data.cal.peak_pos, 'w')
    hold off
    but = [1 1];
    zoom_limits = [1 size(data.pings.Sp, 2) 1 size(data.pings.Sp, 1)];
    disp('Left click and left click to define regions to remove')
    disp('Left click and Right click to restore deleted regions')
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
        elseif but(1) == 1 && but(2) == 3
            sort(xi);
            limits = [floor(xi(1)) ceil(xi(2))];
            data.pings.Sp(:, limits(1):limits(2)) = originalSp(:, limits(1):limits(2));
            data.cal.valid(limits(1):limits(2)) = true;
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
clear originalSp; % Not neeeded anymore

% remove all transmits for which we didn't have suitable echoes (because
% there was no polygon around them, or they were blanked out for some
% reason).

data.pings.Sp = double(data.pings.Sp(:, data.cal.valid));
data.pings.Sv = double(data.pings.Sv(:, data.cal.valid));
data.pings.power = double(data.pings.power(:, data.cal.valid));
data.pings.alongship = double(data.pings.alongship(:, data.cal.valid));
data.pings.athwartship = double(data.pings.athwartship(:, data.cal.valid));
data.cal.peak_pos = data.cal.peak_pos(data.cal.valid);
data.pings.es60_error = data.pings.es60_error(:, data.cal.valid);
data.cal.start_sample = start_sample;
data.cal.sphere_ts = sphere_ts;

save(save_filename, 'data')
process_data(data, scc_revision)

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% This function does the calibration analysis
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
function process_data(data, scc_revision)

% Extract and derive some data from the .raw files
sample_interval = double(data.cal.params.soundvelocity * data.pings.sampleinterval * 0.5); % [m]

% These are no longer used, but kept here just in case they are wanted in
% the future.
%angle_factor = (data.config.anglesensitivityalongship + data.config.anglesensitivityathwartship)/2; % [dB]
%ba_db = data.config.equivalentbeamangle; % [dB re 1 steradian], two-way equivalent beam angle

% Pick out the peak amplitudes for use later on, and discard the
% rest. For Sv and power keep the 9 samples that surround the peak too.
pp = data.cal.peak_pos;
tts = zeros(size(pp));
ssv = zeros(length(pp),9);
range = zeros(length(pp),1);
along = zeros(size(pp))';
athwart = zeros(size(pp))';
power = zeros(length(pp),9);

for j = 1:length(pp)
    if (4 < pp(j) & (pp(j) + 4) < size(data.pings.Sp,1))
%         try
            tts(j) = data.pings.Sp(pp(j), j);
            along(j) = data.pings.alongship(pp(j), j);
            athwart(j) = data.pings.athwartship(pp(j), j);
            ssv(j,:) = data.pings.Sv(pp(j)-4:pp(j)+4, j);
            power(j,:) = data.pings.power(pp(j)-4:pp(j)+4, j);
            range(j) = pp(j);
%         catch ME
%             warning(num2str([pp(j), j]))
%             warning(num2str(size(data.pings.Sp)))
%             warning(num2str(size(data.pings.alongship)))
%             warning(num2str(size(data.pings.athwartship)))
%             warning(num2str(size(data.pings.Sv)))
%             warning(num2str(size(data.pings.power)))
%         end
    end
end
data.cal.ts = tts;
data.cal.sv = ssv;
data.cal.power = power;
% Make the range the range from the transducer in metres
data.cal.range = (range + data.cal.start_sample) * sample_interval;

along = along';
athwart = athwart';
clear tts ssv range pp power

% Extract some useful data from the data structure for convenience
amp_ts = data.cal.ts;
amp_sv = data.cal.sv';
power = data.cal.power';
faBW = data.config.beamwidthalongship; % [degrees]
psBW = data.config.beamwidthathwartship; % [degrees]

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Apply an ES60 triangle wave correction to the data
error = 10.^(data.pings.es60_error/20); % convert from dB to a ratio

amp_ts = amp_ts ./ error';
amp_sv = amp_sv ./ repmat(error,9,1);
power = power ./ repmat(error,9,1);

% And merge some info into one matrix for convenience
sphere = [amp_ts athwart along data.cal.range];

% keep a copy of the original set of data
original.sphere = sphere;
original.amp_sv = amp_sv;
original.power = power;

% The amp_ts and range data are now in sphere
clear amp_ts range error

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Remove any echoes that are grossly wrong.

% trim echoes to those within a little more than the 3 dB beamwidth
% This is not exact because we haven't yet calculated the
% beam centre offsets, but it will do for the moment
trimTo = 1.2 * mean([faBW psBW]) * 0.5;
i = find(abs(sphere(:,2)) < trimTo & abs(sphere(:,3)) < trimTo);
sphere = sphere(i,:);
amp_sv = amp_sv(:,i);
power = power(:,i);

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Use the Simrad theoretical beampattern formula to trim echoes that are
% grossly wrong
maxdBDiff = 6; % any point more than maxDbDiff from the theoretical will be discarded as an outlier
theoreticalTS = data.cal.sphere_ts - simradBeamCompensation(faBW, psBW, sphere(:,3), sphere(:,3));
diffTS = theoreticalTS - sphere(:,1);
i = find(abs(diffTS) <= maxdBDiff);
sphere = sphere(i,:);
amp_sv = amp_sv(:,i);
power = power(:,i);

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Fit the simrad beam pattern to the data. We get estimated beamwidth,
% offsets, and peak value from this.
[offset_fa faBW offset_ps psBW pts_used peak_ts exitflag] = ...
    fit_beampattern(sphere(:,1), sphere(:,2), sphere(:,3), 1.0, mean([faBW psBW]));

if exitflag ~= 1 % failed to converge
    disp('Failed to fit the simrad beam pattern to the data. ')
    disp('This probably means that the beampattern is so far from circular ')
    disp('that there is something wrong with the Ex60.')
    % set some do-nothing values in-lieu of the fitted parameters
    offset_fa = 0;
    offset_pos = 0;
    pts_used = 1:length(sphere(:,1));
    peak_ts = max(sphere(:,1));
    faBW = data.config.beamwidthalongship;
    psBW = data.config.beamwidthathwartship;
    % plot the probably wrong data, using the un-filtered dataset
    [XI YI]=meshgrid(-trimTo:.1:trimTo,-trimTo:.1:trimTo);
    ZI = griddata(original.sphere(:,2), original.sphere(:,3), original.sphere(:,1), XI, YI);
    contourf(XI, YI, ZI)
    hold on
    plot(sphere(:,2), sphere(:,3),'+','MarkerSize',2,'MarkerEdgeColor',[.5 .5 .5])
    % add some circles to indicate what a circular beam looks like...
    for r = 1:4
        x = r * cos(0:.01:2*pi);
        y = r * sin(0:.01:2*pi);
        plot(x, y, 'k')
    end
    colorbar
    axis square
    hold off
    disp(' ')
    disp('Contour plot is of all data points (no filtering, beyond what you did manually).')
    disp(' ')
    disp('No further analysis will be done. The beam is odd.')
    disp(' ')
    disp(['Produced using version ' scc_revision ' of this Matlab function'])
    return
end

% apply the offsets to the target angles
sphere(:,2) = sphere(:,2) - offset_ps;
sphere(:,3) = sphere(:,3) - offset_fa;

% convert the angles to conical angle for use later on
t1 = tan(deg2rad(sphere(:,2)));
t2 = tan(deg2rad(sphere(:,3)));
phi = rad2deg(atan(sqrt(t1.*t1 + t2.*t2)));
theta = rad2deg(atan2(t1, t2));

% Calculate beam compensation for each echo
compensation = simradBeamCompensation(faBW, psBW, sphere(:,3), sphere(:,2));

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Filter outliers based on the beam compensated corrected data
maxdBDiff = 1;
i = find(sphere(:,1)+compensation <= peak_ts+maxdBDiff & ...
    sphere(:,1)+compensation > peak_ts-maxdBDiff);
sphere = sphere(i,:);
compensation = compensation(i);
phi = phi(i);
theta = theta(i);
amp_sv = amp_sv(:,i);
power = power(:,i);

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Calculate the mean_ts from echoes that are on-axis
on_axis = 0.015 * mean(faBW + psBW); % echoes within 1.5% of the beamangle are taken to be on axis. 

% if there are no echoes found within 1.5%, make a note to enlarge the
% search angle.
use_corrected = 0;
if isempty(find(phi < on_axis, 1))
    use_corrected = 1;
end

if use_corrected == 0
    i = find(phi < on_axis);
    mean_ts_on_axis = 20*log10(mean(10.^(sphere(i,1)/20)));
    std_ts_on_axis = std(sphere(i,1));
else
    % since we're using data from a much larger angle range, apply the beam
    % pattern compensation to avoid gross errors.
    i = find(phi < on_axis*5);
    mean_ts_on_axis = 20*log10(mean(10.^((sphere(i,1)+compensation(i))/20)));
    std_ts_on_axis = std(sphere(i,1)+compensation(i));
end

if use_corrected == 0
    oa = num2str(on_axis);
else
    oa = num2str(on_axis*10);
end

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Produce plots and output text

% The calibration results
disp(' ')
disp(['Mean ts within ' oa ' deg of centre = ' num2str(mean_ts_on_axis) ' dB'])
disp(['Std of ts within ' oa ' deg of centre = ' num2str(std_ts_on_axis) ' dB'])
disp(['Number of echoes within ' oa ' deg of centre = ' num2str(length(sphere(i,1)))])
disp(['On axis TS from beam fitting = ' num2str(peak_ts) ' dB.'])
disp(['The sphere ts is ' num2str(data.cal.sphere_ts) ' dB'])
outby = data.cal.sphere_ts - peak_ts;
if outby > 0
    disp(['Hence Ex60 is reading ' num2str(outby) ' dB too low'])
else
    disp(['Hence Ex60 is reading ' num2str(abs(outby)) ' dB too high'])
end

disp(['So add ' num2str(-outby/2) ' dB to G_o'])

disp(['G_o from .raw file is ' num2str(data.config.gain) ' dB'])
disp(' ')
disp(['So the calibrated G_o = ' num2str(data.config.gain-outby/2) ' dB'])
disp(' ')

% Do a contour plot to show the beam pattern
clf
[XI YI]=meshgrid(-trimTo:.1:trimTo,-trimTo:.1:trimTo);
warning('off','MATLAB:griddata:DuplicateDataPoints');
ZI=griddata(double(sphere(:,2)), double(sphere(:,3)), double(sphere(:,1)+outby),XI,YI);
warning('on','MATLAB:griddata:DuplicateDataPoints');
contourf(XI,YI,ZI)
disp('This figure is for a visual quality check of the beam pattern')
axis equal
grid
xlabel('port/starboard angle (\circ)')
ylabel('fore/aft angle (\circ)')
colorbar
disp('Press a key to add the sphere positions to this figure')
pause
% and plot the positions of the sphere. note that there is a bug in matlab
% where the point (.) marker doesn't have a continuous size range, so we
% used a marker that does (the available . sizes are not right).
hold on
plot(sphere(:,2), sphere(:,3),'+','MarkerSize',2,'MarkerEdgeColor',[.5 .5 .5])
axis equal
disp('Press any key to continue')
pause

% Do a 3d plot of the uncorrected and corrected beampattern
clf
surf(XI, YI, ZI)
warning('off','MATLAB:griddata:DuplicateDataPoints');
ZI=griddata(double(sphere(:,2)), double(sphere(:,3)), double(sphere(:,1)+compensation+outby),XI,YI);
warning('on','MATLAB:griddata:DuplicateDataPoints');
hold on
surf(XI, YI, ZI)
zlabel('TS (dB re 1m^2)')
xlabel('Port/stbd angle (\circ)')
ylabel('Fore/aft angle (\circ)')
disp('Press any key to continue')
pause

% Do a plot of the sphere range during the calibration
clf
plot(sphere(:,4))
disp(['Mean sphere range = ' num2str(mean(sphere(:,4))) ...
    ' m, std = ' num2str(std(sphere(:,4))) ' m.'])
disp('The sphere range during the calibration.')
disp('Press any key to continue')
pause

% Do a plot of the compensated and uncompensated echoes at a selection of
% angles, similar to what one can get from the Simrad calibration program
tol = 0.1; % [degrees]
plotBeamSlices(sphere, outby, trimTo, faBW, psBW, peak_ts, tol)
disp('This figure is intended for including in the calibration report')

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Calculate the sa correction value informed by draft formulae
% in Tody Jarvis's WGFAST calibration report.
%
% This is a litte hard to represent in text, so refer to Echoview help for
% more details than are presented here.
% The Sa correction is a value that corrects for the received pulse having
% less energy in it than that nominal, transmitted pulse. The formula for
% Sv (see Echoview) includes a term -10log10(Teff) (where Teff is the
% effective pulse length). We don't have Teff, so need to calculate it. We
% do have Tnom (the nominal pulse length) and just need to scale Tnom so
% that it gives the same result as the integral of Teff:
%
% Teff = Tnom * alpha
% alpha = Teff / Tnom
% alpha = Int(P.dt) / (Pmax * Tnom)
%  where P is the power measurements throughout the echo, 
%  Pmax is the max power in the echo, and dt the time
%  between P measurements. This is simply the ratio of the area under the
%  nominal pulse and the area under the actual pulse.
%
% For the EK60, dt = Tnom/4 (it samples 4 times every pulse length)
% So, alpha = Sum(P * Tnom) / (4 * Pmax * Tnom)
%     alpha = Sum(P) / (4 * Pmax)
%
% However, Echoview, etc, expect the correction factor to be in dB, and
% furthermore is used as (10log10(Tnom) + 2 * Sa). Hence
% Sa = 0.5 * 10log10(alpha)

% Work in the linear domain to calculate the scale factor to convert the
% nominal pulse length into the effective pulse length
alpha = mean( (sum(10.^(power(:,i)/10)) ) ./ (4 * max(10.^(power(:,i)/10)) ));


% And convert that to dB, taking account of how this ratio is used as 2Sa
% everywhere (i.e., it needs to be halved after converting to dB).
sa_correction = 5 * log10(alpha);
disp(' ')
disp(['So sa correction = ' num2str(sa_correction) ' dB'])
disp(' ')
disp(['(the effective pulse length = ' num2str(alpha) ' * nominal pulse length)'])
disp(' ')

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Print out some more cal results
disp(['Fore/aft beamwidth = ' num2str(faBW) ' degrees'])
disp(['Fore/aft offset = ' num2str(offset_fa) ' degrees (to be subtracted from EK60 angles)'])
disp(['Port/stbd beamwidth = ' num2str(psBW) ' degrees'])
disp(['Port/stbd offset = ' num2str(offset_ps) ' degrees (to be subtracted from EK60 angles)'])

disp(['Results obtained from ' num2str(length(sphere(:,1))) ' sphere echoes'])
disp(['Using c = ' num2str(data.cal.params.soundvelocity) ' m/s'])
disp(['Using alpha = ' num2str(data.cal.params.absorptioncoefficient*1000) ' dB/km'])

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Calculate the RMS fit to the beam model
fit_out_to = mean([psBW faBW]) * 0.5; % fit out to half the beamangle
i = find(phi <= fit_out_to);
beam_model = peak_ts - compensation;
% Note: FAST doc halves the difference, otherwise is the same as here. I
% think that the draft FAST report is wrong.
rms_fit = sqrt( mean( (sphere(i,1) - beam_model(i)).^2 ) );
disp(['RMS of fit to beam model out to ' num2str(fit_out_to) ' degrees = ' num2str(rms_fit) ' dB'])

disp(['Produced using version ' scc_revision ' of this Matlab function'])

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
function [offset_fa, bw_fa, offset_ps, bw_ps, pts_used, peak, exitflag] ...
    = fit_beampattern(ts, echoangle_ps, echoangle_fa, limit, bw)

% A function to estimate the beamwidth from the given data. ts and echoangle
% should be the same size and contain the target strength and respective
% angles. TS points more than limit dB away from the fit are
% discarded and a new fit calculated.

% use the Simrad beam compensation equation and fit it to the data
% define a function that can be minimised to find the beamwidth and angle
% offset (also finds the best max amplitude).
% x(1) is the fa beamwidth, x(2) the ps beamwidth, x(3) the fa offset, 
% x(4) the ps offset and x(5) the ts on beam-axis
shape = @(x) sum((ts - x(5) + simradBeamCompensation(x(1), x(2), echoangle_fa-x(3), echoangle_ps-x(4))) .^2);
[result fval exitflag output] = fminsearch(shape, double([bw, bw, 0.0, 0.0, max(ts)]));

bw_fa = result(1);
bw_ps = result(2);
offset_fa = result(3);
offset_ps = result(4);
peak = result(5);

% idenitfy and ignore points that are too far from the fit and recalculate
% (removes some sensitivity to outliers)
% find all points within limit dB of the theoretical beam patter
ii = find(abs(ts - peak + simradBeamCompensation(bw_fa, bw_ps, echoangle_fa-offset_fa, echoangle_ps-offset_ps)) < limit);
% a new function to minimise that only uses the points from ii
shape = @(x) sum((ts(ii) - x(5) + simradBeamCompensation(x(1), x(2), echoangle_fa(ii)-x(3), echoangle_ps(ii)-x(4))) .^2);
result = fminsearch(shape, [bw_fa, bw_ps, offset_fa, offset_ps, peak]);
bw_fa = result(1);
bw_ps = result(2);
offset_fa = result(3);
offset_ps = result(4);
peak = result(5);
pts_used = ii;

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
function compensation = simradBeamCompensation(faBW, psBW, faAngle, psAngle)
% Calculates the simard beam compensation given the beam angles and
% positions in the beam

part1 = 2*faAngle/faBW;
part2 = 2*psAngle/psBW;

compensation = 6.0206 * (part1.^2 + part2.^2 - 0.18*part1.^2.*part2.^2);
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
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

function [phi, theta] = simradAnglesToSpherical(fa, ps)

% convert the angles to conical angle.
t1 = tan(deg2rad(ps));
t2 = tan(deg2rad(fa));
phi = rad2deg(atan(sqrt(t1.*t1 + t2.*t2)));
theta = rad2deg(atan2(t1, t2));

% Convert the angles so that we get negative phi's to allow for plotting of
% complete beam pattern slice arcs
i = find(theta < 0);
phi(i) = -phi(i);
theta(i) = 180+theta(i);

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
function plotBeamSlices(sphere, outby, trimTo, faBW, psBW, peak_ts, tol)
% Produce a plot of the sphere echoes and the fitted beam pattern at 4
% slices through the beam
%
% trimTo is the angle (degrees) to plot out to
% tol is the angle (degrees) to take sphere echoes from for each slice

% suplot1 is a function that produces better looking subplots
subplot1(2,2)

% 0 degrees
subplot1(1)
i = find(abs(sphere(:,2)) < tol);
plot(sphere(i,3), sphere(i,1)+outby,'.')
hold on
x = -trimTo:.1:trimTo;
plot(x, peak_ts + outby - simradBeamCompensation(faBW, psBW, x, 0), 'k');

% 45 degrees. Needs special treatment to get angle off axis from the fa and
% ps angles
subplot1(2)
i = find(abs(sphere(:,2) - sphere(:,3)) < tol);
[phi_x theta_x] = simradAnglesToSpherical(sphere(i,3), sphere(i,2));
s = sphere(i,1) + outby;
i = find(abs(phi_x) <= trimTo);
plot(phi_x(i), s(i), '.')
hold on
[phi_x theta_x] = simradAnglesToSpherical(x, x);
beam = peak_ts + outby - simradBeamCompensation(faBW, psBW, x, x);
i = find(abs(phi_x) <= trimTo);
plot(phi_x(i), beam(i), 'k');

% 90 degrees
subplot1(3)
i = find(abs(sphere(:,3)) < tol);
plot(sphere(i,2), sphere(i,1)+outby,'.')
hold on
x = -trimTo:.1:trimTo;
plot(x, peak_ts + outby - simradBeamCompensation(faBW, psBW, 0, x), 'k');
xlabel('Angle (\circ) off normal')
ylabel('TS (dB re 1m^2)')

% 135 degrees. Needs special treatment to get angle off axis from the fa and
% ps angles
subplot1(4)
i = find(abs(-sphere(:,2) - sphere(:,3)) < tol);
[phi_x theta_x] = simradAnglesToSpherical(sphere(i,3), sphere(i,2));
s = sphere(i,1) + outby;
i = find(abs(phi_x) <= trimTo);
plot(phi_x(i), s(i),'.')
hold on
[phi_x theta_x] = simradAnglesToSpherical(-x, x);
beam = peak_ts + outby - simradBeamCompensation(faBW, psBW, -x, x);
i = find(abs(phi_x) <= trimTo);
plot(phi_x(i), beam(i), 'k');

% make the y-axis limits the same for all 4 subplots
limits = [1000 -1000 1000 -1000];
for i = 1:4
    subplot1(i)
    lim = axis;
    limits(1) = min(limits(1), lim(1));
    limits(2) = max(limits(2), lim(2));
    limits(3) = min(limits(3), lim(3));
    limits(4) = max(limits(4), lim(4));
end
% expand the axis limits so that axis labels don't overlap
limits(1) = limits(1) - .2; % x-axis, units of degrees
limits(2) = limits(2) + .2; % x-axis, units of degrees
limits(3) = limits(3) - 1; % y-axis, units of dB
limits(4) = limits(4) + 1; % y-axis, units of dB
for i = 1:4
    subplot1(i)
    axis(limits)
end

% add a line to each subplot to indicate which angle the slice is for
% Work out the position for the centre of the angled line
pos = [limits(1)+0.06*(limits(2)-limits(1)) limits(4) - 0.1*(limits(4)-limits(3))];
subplot1(1)
text(pos(1), pos(2), '0\circ')
subplot1(2)
text(pos(1), pos(2), '45\circ')
subplot1(3)
text(pos(1), pos(2), '90\circ')
subplot1(4)
text(pos(1), pos(2), '135\circ')

