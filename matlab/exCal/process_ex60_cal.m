function process_ex60_cal(rawfilenames, save_filename, ...
    es60_zero_error, start_depth, stop_depth, freq, c, alpha, sphere_ts, transceiver)
    % Usage:
    %
    % process_es60_cal(dfilenames, save_filename, es60_zero_error, ...
    %    start_depth, stop_depth, freq, c, alpha, sphere_ts, transceiver)
    % OR
    % process_es60cal(save_filename)
    %
    % Code to calculate the calibration coefficients for es60 and ek60
    % calibration data
    %
    % dfilenames is a cell array of strings that give the full path to
    % Ex60 raw files.
    %
    % save_filename is a filename to either save the partially processed data
    % into, or the filename to retrieve such data from.
    %
    % es60_zero_error is an array of ping numbers at which the es60 error is
    % zero. There should be one number for each raw filename given. A -1
    % will result in no correction being applied, as is appropriate for
    % EK60 data. If a single -1 is given, that is used for all raw files.
    %
    % start_depth is the depth [m] to load data from, and stop_depth the depth
    % to load data to. These need to bracket the sphere echo in the
    % echogram. Try a wide range first and then narrow it down. Loading too
    % great a depth range can cause a Matlab out of memory error.
    %
    % freq is the sounder freq in Hz. The raw files need to contain data for
    % this frequency.
    %
    % alpha is the average sound absorption [dB/km] at the given freq for the
    % water between the transducer and the sphere.
    %
    % c is the average sound speed [m/s] for the water between the transducer
    % and the sphere.
    %
    % sphere_ts is the TS of the sphere [dB]. This is optional, and defaults
    % to a frequency specific default value if not given.
    %
    % transceiver is an integer which allows the user to specify which GPT's
    % data to use. Usually in a multi-channel file the individual channel is
    % selected by frequency. If, however, there is a multi-channel file with
    % several GPTs on the same frequency but using different transducers this
    % parameter allows selection of the desired data. Default is 1.

    % HOW TO USE THIS CODE
    %
    % There are two parts to this code:
    % 1. Drawing a region on an echogram that includes the echo from the
    %    calibration sphere, then blanking out regions of questionable data.
    % 2. Processing the sphere data to yield calibration parameters and
    %    graphs.
    %
    % Part 1 involves some work on your part, part 2 almost nothing.
    % 
    % - Call this function with appropriate input parameters.
    % - Using the mouse, left-click to define a region that includes the
    %   echoes from the sphere. Instructions on how to close the
    %   region are given in the Matlab command window.
    % - The code will indicate the detected sphere echo with a white line
    % - Blank out ping ranges using the left mouse button where the
    %   detected sphere echo is wrong (e.g., interference from fish, sphere
    %   moved out of the beam, etc). Instructions on how to end this mode
    %   and undo a blanking are given in the Matlab command window.
    % - This is the end of part 1. Intermediate results are saved to disk.
    % 
    % Part 2 continues on from part 1, or can be run separately.
    %
    % - View the resulting graphs (there is a pause between each graph - 
    %   you'll need to press a key to get to the next graph. Cut&paste as 
    %   necessary for inclusion in a calibration report.
    % - Note the output in the Matlab command window where you'll find the
    %   calibration parameters.
    % - It is suggested that you cut/paste this output into a text file for
    %   later reference.
    
    % ES60/70 TRIANGLE WAVE ERROR
    % 
    % This code will correct for the triangle wave error. It does this by
    % expecting to be told the ping number at which the triangle wave error
    % is zero. This ping number can be obtained from another function, used
    % like this:
    %
    % e = estimate_es60_zero_error_ping('L0001-XX-YY.raw', 38000);
    %
    % Use the value returned in e as the 'es60_zero_error' input to the
    % process_ex60_cal() function. The estimate_es60...() function will
    % produce a plot of the uncorrected and corrected es60/70 data.

    % REQUIREMENTS
    %
    % - Rick Towler's EchoLab toolbox for reading .raw data files.
    %    Works with version 4-16-10.
    % - The subplot1 function from the MathWorks File Exchange
    % - Gavin Macaulay's es60_error() and estimate_es60_zero_error_ping() 
    %    Matlab functions.
    % 

    % ACKNOWLEDGEMENTS
    %
    % Written by Gavin Macaulay while employed at New Zealand's National
    % Institute of Water and Atmospheric Research Ltd, PO Box 14-901,
    % Kilbirnie, Wellington, New Zealand.
    % http://www.niwa.co.nz
    %

    % Optional single target and sphere processing parameters:
    %
    % The std of the arrival angle of each sample in each echo has to be
    % less than or equal to this value for an echo to be kept.
    p.max_std_phase = .3; %[degrees]
    
    % Only consider echoes that have an angular position that is within
    % trimToFactor times the beam angle
    p.trimToFactor = 1.7;
    
    % Any sphere echo more than maxDbDiff1 from the theoretical will be
    % discarded as an outlier. Used in a coarse filter prior to actually
    % working out the beam width.
    p.maxdBDiff1 = 6;
    
    % Beam compensated TS values more than maxdBDiff2 dB above or below the
    % sphere TS are discarded. Done after working out the beam width.
    % Note that this forces an upper limit on the RMS of the final fit to the
    % beam pattern.
    p.maxdBDiff2 = .75;
    
    % All echoes within onAxisTol degrees of an axis (or 45 deg to the axis)
    % will be used when doing the 4-panel plot of sphere echoes
    p.onAxisTol = 0.3; % [degrees]
    
    % All echoes within p.onAxisFactor times the beam width will be considered to
    % be on-axis for the purposes of working out the on-axis gain.
    p.onAxisFactor = 0.015; % [factor]
    
    % If there are less than p.minOnAxisEchos sphere echoes close to the
    % beam centre (as calculated using p.onAxisFactor), use
    % p.onAxisFactorExpanded instead.
    p.minOnAxisEchoes = 6;
    
    % If insufficient echoes are found with p.onAxisFactor multiplied by
    % the average of the fore/aft and port/stbd beamwidths,
    % p.onAxisFactorExpanded will be used instead.
    p.onAxisFactorExpanded = 5 * p.onAxisFactor; % [factor]
    
    % When calculating the RMS fit of the data to the Simrad beam pattern, only
    % consider echoes out to (rmsOutTo * beamwidth) degrees.
    p.rmsOutTo = 0.5;

    % What colour map to use for the echogram
    p.colourmap = ''; % '' or 'EK500'
    p.SpRange = [-72 -36];
    
    % What method to use when calculating the 'best' estimate of the on-axis
    % sphere TS. Max of on-axis echoes, mean of on-axis echoes, or the peak of
    % the fitted beam pattern.
    p.onAxisMethod = 'mean'; % choices of 'max', 'mean', or 'beam fitting'
    
    p.maximiseEchogram = false; % make the echogram window take up the whole screen
    
    % Test to stamp the output with (e.g., a version number). Only works
    % automatically if you keep this code in the subversion source code
    % control system. 
    scc_revision = '$Revision$';
    % pick out just the number
    scc_revision = regexprep(scc_revision, '[^\d]', '');
    if isempty(scc_revision)
        scc_revision = 'unknown';
    end

    % Load in partially processed data and re-run the final set of processing.
    if nargin == 1
        load(rawfilenames) % not real rawfilename, but rather save_filename
        process_data(data, p, scc_revision)
        return
    end

    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    % Part 1: Selection of echogram regions that contain sphere echoes
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    
    % If es60_zero_error value indicates that we dealing with EK60 data, make
    % sure that we have a value for each EK60 raw file that is to be processed.
    if length(es60_zero_error) == 1 && es60_zero_error(1) == -1
        es60_zero_error = -1 * ones(1, length(rawfilenames));
    end

    % Load in all of the .raw files and merge the data together for subsequent
    % processing.

    for i = 1:length(rawfilenames)
        % Read in the first ping to get the parameters that are needed to
        % convert the given depth ranges into sample range.

        disp(['Loading raw file: ' rawfilenames{i} '.'])
        [h d] = readEKRaw(rawfilenames{i}, 'Frequencies', freq, 'GPS', 0, 'PingRange', [1 1], ...
            'SampleRange', [1 1]);
        
        if (h.transceivercount > 1) && ~exist('transceiver','var')
            warning('No transceiver specified for multi-transceiver data. Using transceiver 1.')
        end
        
        if ~exist('transceiver','var')
            transceiver = 1;
        end

        start_sample = round(2 * start_depth / (d.pings(transceiver).sampleinterval * c));
        if start_sample == 0
            start_sample = 1;
        end
        stop_sample = round(2* stop_depth / (d.pings(transceiver).sampleinterval * c));

        % And then read in the data for real, selecting just that between the
        % given start and end ranges.
        [h d] = readEKRaw(rawfilenames{i}, 'Frequencies', freq, 'GPS', 0, 'PingRange', [1 Inf],...
            'SampleRange', [start_sample stop_sample]);

        if (h.transceivercount) > 1           % Take only the data for the desired transciever
            fieldnamelist = fieldnames(d);    % looping over all fields in structure d
            for j = 1:length(fieldnamelist)
                dtemp.(fieldnamelist{j}) = d.(fieldnamelist{j})(transceiver);
            end
            d = dtemp;
            clear dtemp;
        end
        
        % Calculate the correction for the ES60 triange wave error if required - it is
        % applied later.
        d.cal.es60_zero_error = es60_zero_error;

        num_pings = size(d.pings.power, 2);
        if es60_zero_error(i) >= 0
            d.pings.es60_error = es60_error((1:num_pings)-es60_zero_error(i));
        else
            d.pings.es60_error = zeros(1, num_pings);
        end

        % Merge the current file into one dataset, keeping just the data
        % necessary of subsequent processing to save on memory consumption.
        if i == 1
            header = h;
            data = d;
        elseif i > 1
            data.pings.power = [data.pings.power d.pings.power];
            if isfield(data.pings, 'alongship_e')
                data.pings.alongship_e = [data.pings.alongship_e d.pings.alongship_e];
                data.pings.athwartship_e = [data.pings.athwartship_e d.pings.athwartship_e];
            end
            data.pings.es60_error = [data.pings.es60_error d.pings.es60_error];
	        data.pings.time = [data.pings.time d.pings.time];
        end
    end

    % Keep for our records.
    data.cal.es60_error = es60_zero_error;

    % Work out the sphere TS if it hasn't been given
    if nargin == 8
        % data.pings.frequency is as long as the number of pings, so just use
        % the first ping for this call.
        sphere_ts = getSphereTS(data.pings.frequency(1));
    end

    % Override some of the cal parameters based on CTD data
    calParams = readEKRaw_GetCalParms(header, data);

    if nargin > 6
        calParams.soundvelocity = c;
        calParams.absorptioncoefficient = alpha/1000; % convert to dB/m
    end

    % Get Sp version of the actual echo samples with correction for true
    % range to the sphere echo.
    data = readEKRaw_Power2Sp(data, calParams, 'KeepPower', true, 'tvgcorrection', true);
    data = readEKRaw_ConvertAngles(data, calParams);

    % Keep the cal params around for later
    data.cal.params = calParams;

    % Throw away lots of stuff that we don't need to save on memory...
    data.pings.transducerdepth = data.pings.transducerdepth(1);
    data.pings.frequency = data.pings.frequency(1);
    data.pings.transmitpower = data.pings.transmitpower(1);
    data.pings.pulselength = data.pings.pulselength(1);
    data.pings.bandwidth = data.pings.bandwidth(1);
    data.pings.sampleinterval = data.pings.sampleinterval(1);
    data.pings.soundvelocity = data.pings.soundvelocity(1);
    data.pings.absorptioncoefficient = data.pings.absorptioncoefficient(1);

    %%%%%%%%%%%
    % Now comes the interactive part where the user draws a polygon that
    % contain the sphere. We then find the index into the acoustic data for
    % each ping that has the largest echo amplitude (and hopefully
    % the peak of the sphere echo) in the given bounds.
    warning('off','MATLAB:log:logOfZero')

    figure('name', 'Choose sphere echoes...')
    hold off
    clf
    imagesc(data.pings.Sp)
    colormap(getColormap(p.colourmap))
    caxis([p.SpRange])
    xlabel('Pings')
    ylabel('Samples')
    
    if p.maximiseEchogram
        screenSize = get(0, 'ScreenSize');
        set(gcf, 'Position', [0 1 screenSize(3) screenSize(4)])
    end
    
    disp('Use the left mouse button to pick points that define a polygon on the echogram.')
    disp('Use the right mouse button to pick the last point and close the polygon.')
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

    % Store the vertices of the polygon.
    data.cal.polygon = xy;

    % Now pick the max amplitude point for each ping from within the depths given by
    % the polygon that the user has just drawn.
    num_pings = size(data.pings.Sp, 2);
    num_samples = size(data.pings.Sp, 1);
    [X Y] = meshgrid(1:num_pings, 1:num_samples);

    % Find all points in X Y that are inside the user drawn polygon
    in = inpolygon(X, Y, xy(:,1), xy(:,2));

    % Get the max and min sample (row) number and use them as bounds for
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

    % Show the echoes that have been choosen for the user to check, and let
    % them blank out pings ranges.
    originalSp = data.pings.Sp; % So we can restore/undo deleted regions

    for i = 1:length(data)
        clf
        imagesc(data.pings.Sp)
        hold on
        plot(data.cal.peak_pos, 'w')
        hold off
        but = [1 1];
        zoom_limits = [1 size(data.pings.Sp, 2) 1 size(data.pings.Sp, 1)];
        disp('Left click and left click to define ping regions to remove')
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
                limits = [max(1, floor(xi(1))) min(ceil(xi(2)), length(data.cal.valid))];
                data.pings.Sp(:, limits(1):limits(2)) = -120; % a low value
                data.cal.valid(limits(1):limits(2)) = false;
                imagesc(data.pings.Sp)
                colormap(getColormap(p.colourmap))
                caxis([p.SpRange])
                hold on
                plot(data.cal.peak_pos, 'w')
                axis(zoom_limits)
                hold off
            elseif but(1) == 1 && but(2) == 3
                sort(xi);
                limits = [max(1, floor(xi(1))) min(ceil(xi(2)), length(data.cal.valid))];
                data.pings.Sp(:, limits(1):limits(2)) = originalSp(:, limits(1):limits(2));
                data.cal.valid(limits(1):limits(2)) = true;
                imagesc(data.pings.Sp)
                colormap(getColormap(p.colourmap))
                caxis([p.SpRange])
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

    % Remove all transmits for which we didn't have suitable echoes (because
    % there was no polygon around them, or they were blanked out for some
    % reason).

    data.pings.Sp = double(data.pings.Sp(:, data.cal.valid));
    data.pings.power = double(data.pings.power(:, data.cal.valid));
    if isfield(data.pings, 'alongship')
        data.pings.alongship = double(data.pings.alongship(:, data.cal.valid));
        data.pings.athwartship = double(data.pings.athwartship(:, data.cal.valid));
    end
    data.cal.peak_pos = data.cal.peak_pos(data.cal.valid);
    data.pings.es60_error = data.pings.es60_error(:, data.cal.valid);
    data.cal.sphere_ts = sphere_ts;

    % And save the data to date.
    save(save_filename, 'data')

    % Now that the appropriate pings, etc, are selected, do the actual
    % calibration processing.
    process_data(data, p, scc_revision)
end

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Part 2: Processing of sphere echoes to yield calibration parameters
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
function process_data(data, p, scc_revision)

    % Extract and derive some data from the .raw files
    
    % Pick out the peak amplitudes for use later on, and discard the
    % rest. For power keep the 9 samples that surround the peak too.
    pp = data.cal.peak_pos;
    % exclude echoes that are too close to the start or end of the sample
    % data (+/-4 samples above/below the peak are required for later
    % processing).
    inRange = find(4 < pp & (pp + 4) < size(data.pings.Sp,1));
    tts = zeros(size(inRange));
    range = zeros(length(inRange),1);
    along = zeros(size(inRange))';
    athwart = zeros(size(inRange))';
    power = zeros(length(inRange),9);
    phase_along = zeros(length(inRange),9);
    phase_athwart = zeros(length(inRange),9);
    
    for i = 1:length(inRange)
        j = inRange(i);
        tts(i) = data.pings.Sp(pp(j), j);
        along(i) = data.pings.alongship(pp(j), j);
        athwart(i) = data.pings.athwartship(pp(j), j);
        power(i,:) = data.pings.power(pp(j)-4:pp(j)+4, j);
        phase_along(i,:) = data.pings.alongship(pp(j)-4:pp(j)+4, j);
        phase_athwart(i,:) = data.pings.athwartship(pp(j)-4:pp(j)+4, j);
        range(i) = pp(j);
    end
    
    data.cal.ts = tts;
    data.cal.power = power;
    
    % Convert the range from samples to metres. Range to the peak target
    % amplitude is counted from the peak of the transmit pulse, which is
    % taken to occur at the range corresponding to half the transmit pulse
    % length.
    data.cal.range = data.pings.range(range) - ...
        data.pings.pulselength * data.cal.params.soundvelocity/4;
    
    along = along';
    athwart = athwart';
    phase_along = phase_along';
    phase_athwart = phase_athwart';
    
    clear tts range pp power
    
    % Extract some useful data from the data structure for convenience
    amp_ts = data.cal.ts;
    power = data.cal.power';
    faBW = data.config.beamwidthalongship; % [degrees]
    psBW = data.config.beamwidthathwartship; % [degrees]
    
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    % Apply an ES60 triangle wave correction to the data
    amp_ts = amp_ts - data.pings.es60_error(inRange)';
    power = power - repmat(data.pings.es60_error(inRange), 9, 1);
    
    % And merge some info into one matrix for convenience
    sphere = [amp_ts athwart along data.cal.range];
    
    % Keep a copy of the original set of data
    original.sphere = sphere;
    original.power = power;
    
    % The amp_ts and range data are now in sphere
    clear amp_ts range error inRange
        
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    % Remove any echoes that are likely to be noisy or wrong
    
    % Filter out echoes with too much variation in their position through the echo.
    i = find(std(phase_along(4:6,:)) <= p.max_std_phase & ...
        std(phase_athwart(4:6,:)) <= p.max_std_phase);
    [sphere power phase_along phase_athwart] = trim_data(i, sphere, power, phase_along, phase_athwart);
    
    % Trim echoes to those within a bit more than the 3 dB beamwidth
    % This is not exact because we haven't yet calculated the
    % beam centre offsets, but it will do for the moment
    trimTo = p.trimToFactor * mean([faBW psBW]) * 0.5;
    i = find(abs(sphere(:,2)) < trimTo & abs(sphere(:,3)) < trimTo);
    [sphere power phase_along phase_athwart] = trim_data(i, sphere, power, phase_along, phase_athwart);
    
    % Use the Simrad theoretical beampattern formula to trim echoes that are
    % grossly wrong
    theoreticalTS = data.cal.sphere_ts - simradBeamCompensation(faBW, psBW, sphere(:,3), sphere(:,2));
    diffTS = theoreticalTS - sphere(:,1);
    i = find(abs(diffTS) <= p.maxdBDiff1);
    [sphere power phase_along phase_athwart] = trim_data(i, sphere, power, phase_along, phase_athwart);
    
    % Fit the simrad beam pattern to the data. We get estimated beamwidth,
    % offsets, and peak value from this.
    [offset_fa faBW offset_ps psBW pts_used peak_ts exitflag] = ...
        fit_beampattern(sphere(:,1), sphere(:,2), sphere(:,3), 1.0, mean([faBW psBW]));
    
    % If a beam pattern couldn't be fitted, give up with some diagonistics.
    if exitflag ~= 1
        disp('Failed to fit the simrad beam pattern to the data. ')
        disp('This probably means that the beampattern is so far from circular ')
        disp('that there is something wrong with the echosounder.')
        
        % Plot the probably wrong data, using the un-filtered dataset
        [XI YI]=meshgrid(-trimTo:.1:trimTo,-trimTo:.1:trimTo);
        ZI = griddata(original.sphere(:,2), original.sphere(:,3), original.sphere(:,1), XI, YI);
        contourf(XI, YI, ZI)
        hold on
        plot(sphere(:,2), sphere(:,3),'+','MarkerSize',2,'MarkerEdgeColor',[.5 .5 .5])
        
        % Add some circles to indicate what a circular beam looks like...
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
    
    % Apply the offsets to the target angles
    sphere(:,2) = sphere(:,2) - offset_ps;
    sphere(:,3) = sphere(:,3) - offset_fa;
    
    % Convert the angles to conical angle for use later on
    t1 = tan(sphere(:,2) * pi/180);
    t2 = tan(sphere(:,3) * pi/180);
    phi = atan(sqrt(t1.*t1 + t2.*t2)) * 180/pi;
    
    % Calculate beam compensation for each echo
    compensation = simradBeamCompensation(faBW, psBW, sphere(:,3), sphere(:,2));
    
    % Filter outliers based on the beam compensated corrected data
    i = find(sphere(:,1)+compensation <= peak_ts+p.maxdBDiff2 & ...
        sphere(:,1)+compensation > peak_ts-p.maxdBDiff2);
    [sphere power phase_along phase_athwart] = trim_data(i, sphere, power, phase_along, phase_athwart);
    
    % And some data that trim_data doesn't do
    compensation = compensation(i);
    phi = phi(i);
    
    % Calculate the mean_ts from echoes that are on-axis
    on_axis = p.onAxisFactor * mean(faBW + psBW);
    
    % If there are no echoes found within onAxisFactor, make a note to use
    % a larger factor.
    use_corrected = 0;

    if numel(find(phi < on_axis)) <  p.minOnAxisEchoes
        use_corrected = 1;
    end
    
    if use_corrected == 0
        i = find(phi < on_axis);
        ts_values = sphere(i,1);
        mean_ts_on_axis = 10*log10(mean(10.^(ts_values/10)));
        std_ts_on_axis = std(ts_values);
        max_ts_on_axis = max(ts_values);
    else
        on_axis = p.onAxisFactorExpanded * mean(faBW + psBW);
        % Since we're using data from a much larger angle range, apply the beam
        % pattern compensation to avoid gross errors.
        i = find(phi < on_axis);
        ts_values = sphere(i,1) + compensation(i);
        mean_ts_on_axis = 10*log10(mean(10.^(ts_values/10)));
        std_ts_on_axis = std(ts_values);
        max_ts_on_axis = max(ts_values);
    end
    
    % plot up the on-axis TS values
    figure('name', 'On-axis sphere TS')
    if exist('boxplot', 'file') % this lives in the Statistics toolbox, which not everyone will have
        boxplot(ts_values)
    else
        hist(ts_values)
    end
    ylabel('TS (dB re 1 m^2)')
    title(['On axis TS values for ' num2str(length(sphere(i,1))) ' targets'])
    
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    % Produce plots and output text
    
    % The calibration results
    disp(' ')
    oa = num2str(on_axis);
    disp(['Mean ts within ' oa ' deg of centre = ' num2str(mean_ts_on_axis) ' dB'])
    disp(['Std of ts within ' oa ' deg of centre = ' num2str(std_ts_on_axis) ' dB'])
    disp(['Maximum TS within ' oa ' deg of centre = ' num2str(max_ts_on_axis) ' dB.'])
    disp(['Number of echoes within ' oa ' deg of centre = ' num2str(length(sphere(i,1)))])
    disp(['On axis TS from beam fitting = ' num2str(peak_ts) ' dB.'])
    disp(['The sphere ts is ' num2str(data.cal.sphere_ts) ' dB'])
    
    if strcmp(p.onAxisMethod, 'max')
        outby = data.cal.sphere_ts - max_ts_on_axis;
    elseif strcmp(p.onAxisMethod, 'mean')
        outby = data.cal.sphere_ts - mean_ts_on_axis;
    elseif strcmp(p.onAxisMethod, 'beam fitting')
        outby = data.cal.sphere_ts - peak_ts;
    end
    
    if outby > 0
        disp(['Hence Ex60 is reading ' num2str(outby) ' dB too low (' p.onAxisMethod ' method).'])
    else
        disp(['Hence Ex60 is reading ' num2str(abs(outby)) ' dB too high (' p.onAxisMethod ' method).'])
    end
    
    disp(['So add ' num2str(-outby/2) ' dB to G_o'])
    
    disp(['G_o from .raw file is ' num2str(data.config.gain) ' dB'])
    disp(' ')
    disp(['So the calibrated G_o = ' num2str(data.config.gain-outby/2) ' dB'])
    disp(' ')
    
    % Do a contour plot to show the beam pattern
    figure('name', 'Beam pattern contour plot')
    clf
    [XI YI]=meshgrid(-trimTo:.1:trimTo,-trimTo:.1:trimTo);
    warning('off','MATLAB:griddata:DuplicateDataPoints');
    ZI=griddata(double(sphere(:,2)), double(sphere(:,3)), double(sphere(:,1)+outby),XI,YI);
    warning('on','MATLAB:griddata:DuplicateDataPoints');
    contourf(XI,YI,ZI)
    axis equal
    grid
    xlabel('Port/starboard angle (\circ)')
    ylabel('Fore/aft angle (\circ)')
    colorbar
    
    % And plot the positions of the sphere. Note that there is a bug in matlab
    % where the point (.) marker doesn't have a continuous size range, so we
    % used a marker that does (the available . sizes are not right).
    hold on
    plot(sphere(:,2), sphere(:,3),'+','MarkerSize',2,'MarkerEdgeColor',[.5 .5 .5])
    axis equal
    
    % Do a 3d plot of the uncorrected and corrected beampattern
    figure('name', '3D beam pattern (corrected and uncorrected)')
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
    
    % Do a plot of the sphere range during the calibration
    figure('name', 'Sphere range')
    clf
    plot(sphere(:,4))
    disp(['Mean sphere range = ' num2str(mean(sphere(:,4))) ...
        ' m, std = ' num2str(std(sphere(:,4))) ' m.'])
    title('Sphere range during the calibration.')
    xlabel('Ping number')
    ylabel('Sphere range (m)')
    
    % Do a plot of the compensated and uncompensated echoes at a selection of
    % angles, similar to what one can get from the Simrad calibration program
    figure('name', 'Beam slice plot')
    clf
    plotBeamSlices(sphere, outby, trimTo, faBW, psBW, peak_ts, p.onAxisTol)
    
    % Calculate the sa correction value informed by draft formulae
    % in Tody Jarvis's WGFAST calibration report.
    %
    % This is a litte hard to represent in text, so refer to Echoview help for
    % more details than are presented here.
    %
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
    disp(['So sa correction = ' num2str(sa_correction) ' dB.'])
    disp(' ')
    disp(['(the effective pulse length = ' num2str(alpha) ' * nominal pulse length).'])
    disp(' ')
    
    % Print out some more cal results
    disp(['Fore/aft beamwidth = ' num2str(faBW) ' degrees.'])
    disp(['Fore/aft offset = ' num2str(offset_fa) ' degrees (to be subtracted from EK60 angles).'])
    disp(['Port/stbd beamwidth = ' num2str(psBW) ' degrees.'])
    disp(['Port/stbd offset = ' num2str(offset_ps) ' degrees (to be subtracted from EK60 angles).'])
    
    disp(['Results obtained from ' num2str(length(sphere(:,1))) ' sphere echoes.'])
    disp(['Using c = ' num2str(data.cal.params.soundvelocity) ' m/s.'])
    disp(['Using alpha = ' num2str(data.cal.params.absorptioncoefficient*1000) ' dB/km.'])
    
    % Calculate the RMS fit to the beam model
    fit_out_to = mean([psBW faBW]) * p.rmsOutTo; % fit out to half the beamangle
    i = find(phi <= fit_out_to);
    beam_model = peak_ts - compensation;
    
    % Note that the halving of the difference is not what was done here
    % previously. From a comparison of the rms value that the Simrad
    % calibration program produces and what this formulae produced when using
    % the same input data, it appears that the Simrad program is halving the
    % difference, hence this formula now halves the difference and produces rms
    % values that are directly comparable to the Simrad results.
    rms_fit = sqrt( mean( ( (sphere(i,1) - beam_model(i))/2 ).^2 ) );
    disp(['RMS of fit to beam model out to ' num2str(fit_out_to) ' degrees = ' num2str(rms_fit) ' dB.'])
    
    disp(['Produced using version ' scc_revision ' of this Matlab function ' ...
        'on ' datestr(now())])
end

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
function [offset_fa, bw_fa, offset_ps, bw_ps, pts_used, peak, exitflag] ...
    = fit_beampattern(ts, echoangle_ps, echoangle_fa, limit, bw)
    % A function to estimate the beamwidth from the given data. TS and echoangle
    % should be the same size and contain the target strength and respective
    % angles. TS points more than limit dB away from the fit are
    % discarded and a new fit calculated.

    % Use the Simrad beam compensation equation and fit it to the data.

    % Define a function that can be minimised to find the beamwidth and angle
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

    % Idenitfy and ignore points that are too far from the fit and recalculate
    % (removes some sensitivity to outliers)
    % Find all points within limit dB of the theoretical beam patter
    ii = find(abs(ts - peak + simradBeamCompensation(bw_fa, bw_ps, echoangle_fa-offset_fa, echoangle_ps-offset_ps)) < limit);
    
    % A new function to minimise that only uses the points from ii
    shape = @(x) sum((ts(ii) - x(5) + simradBeamCompensation(x(1), x(2), echoangle_fa(ii)-x(3), echoangle_ps(ii)-x(4))) .^2);
    result = fminsearch(shape, [bw_fa, bw_ps, offset_fa, offset_ps, peak]);
    bw_fa = result(1);
    bw_ps = result(2);
    offset_fa = result(3);
    offset_ps = result(4);
    peak = result(5);
    pts_used = ii;
end

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
function compensation = simradBeamCompensation(faBW, psBW, faAngle, psAngle)
    % Calculates the simard beam compensation given the beam angles and
    % positions in the beam
    
    part1 = 2*faAngle/faBW;
    part2 = 2*psAngle/psBW;
    
    compensation = 6.0206 * (part1.^2 + part2.^2 - 0.18*part1.^2.*part2.^2);
end

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Get a nominal ts for the given frequency
function sphere_ts = getSphereTS(freq)
    % These are from Fisheries Acoustics, Simmonds & MacLennan, 2005.
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
            sphere_ts = NaN;
            disp(['Sphere TS not known for a freq of ' num2str(freq) ' Hz'])
    end
end

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
function [phi, theta] = simradAnglesToSpherical(fa, ps)
    % Convert the cartesian angles to conical angle.
    t1 = tan(ps * pi/180);
    t2 = tan(fa * pi/180);
    phi = atan(sqrt(t1.*t1 + t2.*t2)) * 180/pi; % angle off transducer axis
    theta = atan2(t1, t2) * 180/pi; % rotation about transducer axis
    
    % Convert the angles so that we get negative phi's to allow for plotting of
    % complete beam pattern slice arcs
    i = find(theta < 0);
    phi(i) = -phi(i);
    theta(i) = 180+theta(i);
end

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
function plotBeamSlices(sphere, outby, trimTo, faBW, psBW, peak_ts, tol)
    % Produce a plot of the sphere echoes and the fitted beam pattern at 4
    % slices (0 45, 90, and 135 degrees) through the beam.
    %
    % trimTo is the angle (degrees) to plot out to
    % tol is the angle (degrees) to take sphere echoes from for each slice
    
    % suplot1 is a function that produces better looking subplots, available
    % from the Mathworks File Exchange.
    subplot1(2,2)
    
    % 0 degrees
    subplot1(1)
    i = find(abs(sphere(:,2)) < tol);
    plot(sphere(i,3), sphere(i,1)+outby,'k.')
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
    plot(phi_x(i), s(i), 'k.')
    hold on
    [phi_x theta_x] = simradAnglesToSpherical(x, x);
    beam = peak_ts + outby - simradBeamCompensation(faBW, psBW, x, x);
    i = find(abs(phi_x) <= trimTo);
    plot(phi_x(i), beam(i), 'k');
    
    % 90 degrees
    subplot1(3)
    i = find(abs(sphere(:,3)) < tol);
    plot(sphere(i,2), sphere(i,1)+outby,'k.')
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
    plot(phi_x(i), s(i),'k.')
    hold on
    [phi_x theta_x] = simradAnglesToSpherical(-x, x);
    beam = peak_ts + outby - simradBeamCompensation(faBW, psBW, -x, x);
    i = find(abs(phi_x) <= trimTo);
    plot(phi_x(i), beam(i), 'k');
    
    % Make the y-axis limits the same for all 4 subplots
    limits = [1000 -1000 1000 -1000];
    for i = 1:4
        subplot1(i)
        lim = axis;
        limits(1) = min(limits(1), lim(1));
        limits(2) = max(limits(2), lim(2));
        limits(3) = min(limits(3), lim(3));
        limits(4) = max(limits(4), lim(4));
    end
    
    % Expand the axis limits so that axis labels don't overlap
    limits(1) = limits(1) - .2; % x-axis, units of degrees
    limits(2) = limits(2) + .2; % x-axis, units of degrees
    limits(3) = limits(3) - 1; % y-axis, units of dB
    limits(4) = limits(4) + 1; % y-axis, units of dB
    for i = 1:4
        subplot1(i)
        axis(limits)
    end

    % Add a line to each subplot to indicate which angle the slice is for.
    % Work out the position for the ship schematic with angled line.
    angles = [0 45 90 135]; % angles of the four plots
    for i = 1:length(angles)
        subplot1(i)
        pos = get(gca, 'Position');
        axes('Position', [pos(1)+0.02*pos(3) pos(2)+0.7*pos(4) 0.2*pos(3) 0.2*pos(4)]);
        plot_angle_diagram(angles(i))
    end
end
    
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
function plot_angle_diagram(angle)
    % Plots a little figure of the ship and an angled line on the given axes
    
    % The ship shape
    x = [0 1 1 .5 0 0];
    y = [0 0 2 2.5 2 0];
    plot(x,y,'k')
    hold on
    
    % The circle to represent the transducer
    theta = 0:.01:2.1*pi;
    r = 0.3;
    centre = [0.5 1.5];
    length = 0.9;
    plot(centre(1) + r*cos(theta), centre(2) + r*sin(theta), 'k')
    
    % The angled line
    switch angle
        case 0
            plot([centre(1) centre(1)], [centre(2)-length centre(2)+length], 'k', 'LineWidth', 2)
        case 45
            x = length*cos(angle*pi/180);
            y = length*sin(angle*pi/180);
            plot([centre(1)-x centre(1)+x] ,[centre(2)-y centre(2)+y], 'k', 'LineWidth', 2)
        case 90
            plot([centre(1)-length centre(1)+length], [centre(2) centre(2)], 'k', 'LineWidth', 2)
        case 135
            x = length*cos(angle*pi/180);
            y = length*sin(angle*pi/180);
            plot([centre(1)+x centre(1)-x] ,[centre(2)+y centre(2)-y], 'k', 'LineWidth', 2)
    end
    
    axis equal
    
    % The bottom of some figures get chopped off when removing the axis, so
    % extend the axis a little to prevent this
    set(gca, 'YLim', [-0.1 2.6])
    axis off
    hold off
end

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
function [sphere power phase_along phase_athwart] = trim_data(i, sphere, power, phase_along, phase_athwart)
    % A function to eliminate specified entries in a set of vectors.
    
    sphere = sphere(i,:);
    power = power(:,i);
    phase_along = phase_along(:,i);
    phase_athwart = phase_athwart(:,i);
end

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
function cmap = getColormap(name)

    if strcmp(name, 'EK500')
        cmap = [255   255   255  % white
                159   159   159    % light grey
                95    95    95     % grey
                0     0   255      % dark blue
                0     0   127      % blue
                0   191     0      % green
                0   127     0      % dark green
                255   255     0    % yellow
                255   127     0    % orange
                255     0   191    % pink
                255     0     0    % red
                166    83    60    % light brown
                120    60    40]./255;  % dark brown
    else
        cmap = colormap; % the default
    end
    
end
