function zero_error_ping = estimate_es60_zero_error_ping(rawfilenames, freq, offset)

% zero_error_ping = estimate_es60_zero_error_ping(rawfilenames, freq,
% offset)
%
% A function that tries to find the ping at which an ES60 data file has no
% error from the triangle wave-shaped change in power values.
% 
% It cannot find the relevant ping if there are too few pings in the file.
% In that case, an offset can be supplied. This is the mean power value
% obtained from the same transducer/transcevier from a file that is long
% enough, to which the correction has been applied. The offset is only used
% if a zero error ping number cannot be found.
%
% This function plots the uncorrected and corrected power for each file and
% prints out the zero error ping number and the mean corrected power value.
% 
% The rawfilename parameter should be a cell array of strings.
%
% The freq parameter determines which frequency to use from the file (files
% can have mulitple freqs). Use units of Hz.
%
% Optional offeset has units of dB. I.e. use the mean corrected power value
% obtained from files for which the error correction is successful.
% 
% The zero error ping numbers are returned from the function in an array
% with as many entries as there are raw filenames.
%

% $Id:$

period = 2721;

if nargin == 2
    offset = NaN;
end

% Do the whole thing on a file-by-file basis.
for i = 1:length(rawfilenames)
    [h d] = readEKRaw(rawfilenames{i}, 'Frequencies', freq, 'GPS', 0, 'PingRange', [0 Inf],...
        'SampleRange', [1 1]);
    num_pings = length(d.pings.power);
    fit = struct('std', ones(period,1)*1000, 'mean', zeros(period,1));
	% Apply all possible corrections to the first sample in each ping. Calculate the standard 
	% deviation and mean of the corrected first sample amplitude
    for j = 1:period
        fit.std(j) = std( abs(d.pings.power - es60_error((1:num_pings) + j)) );
        fit.mean(j) = mean( abs(d.pings.power - es60_error((1:num_pings) + j)) );
    end
	% Ideally, the minimum standard deviation will give the appropriate zero error ping number
    [m, zero_error_ping(i)] = min(fit.std);
    % if there are enough pings to guarantee a change in slope in the
    % error, use the value, otherwise investigate a bit more.
    if num_pings < period/2
		% Now check to see if the minimum is a good one	
        std_of_std = std(fit.std);
		% find zero error ping numbers where the std of the fit is close to the minimum
        close_values = find(fit.std < min(fit.std)+0.01*std_of_std);
        if length(close_values) > 40 % too many values close to the minimum, so we don't trust 
			% any of them and use the supplied offset (or ask for one)
            if isnan(offset)
                disp('Cannot find the zero error ping number. You need to manually supply an offset.')
            else % we have been supplied with an offset to use
                disp('Using supplied offset.')
                % Now find the zero error ping number with the corrected mean that is closest to the
                % supplied offset, but still with a low std.
                % This code is not vectorised, but this is not the normal case so the loss in speed 
				% should be acceptable.
                min_with_offset = 100000000;
                for j = 1:length(fit)
                    if (abs(fit.mean(j) - offset) < min_with_offset) && (fit.std(j) < min(fit.std)+0.01*std_of_std)
                        min_with_offset = abs(fit.mean(j)-offset);
                        zero_error_ping(i) = j;
                    end
                end
            end
        else % Is this code good enough? Does there need to be more checking of the result here?
			% We get here if there are less than 40 zero error ping numbers with a low std. If this is the 
			% case, we simply take the zero error ping number with the lowest std.
            mean_corrected_value = mean(d.pings.power - es60_error((1:num_pings)+zero_error_ping(i)));
            disp(['The mean corrected value is ' num2str(mean_corrected_value) ' dB'])
        end
    else
		% There were enough pings to cover a change in slope in the error, so we're done.
        mean_corrected_value = mean(d.pings.power - es60_error((1:num_pings)+zero_error_ping(i)));
        disp(['The mean corrected value is ' num2str(mean_corrected_value) ' dB'])
    end
	
	% Show the correction for visual validation.
    disp(['The zero error ping number is ' num2str(zero_error_ping(i))])
    plot(d.pings.power)
    hold on
    plot(d.pings.power - es60_error((1:num_pings)+zero_error_ping(i)), 'r')
	xlabel('Ping number')
	ylabel('First sample received power?? (dB re 1 W, uncalibrated)')
    legend('Uncorrected', 'Corrected')
    hold off
    disp('Paused. Press any key to continue')
    pause
end

