function zero_error_ping = estimate_es60_zero_error_ping(rawfilenames, freq, offset)

% zero_error_ping = estimate_es60_zero_error_ping(rawfilenames, freq,
% offset)
%
% A function that tries to find the ping at which an ES60 data file has no
% error from the triangle wave-shaped change in power values.
% 
% It cannot find the relevant ping if there are too few pings in the file.
% In that case, an offset can be supplied. This is the mean power value one
% obtains from the same transducer/transcevier from a file that is long
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
% The zero error ping numbers are returned from the function in an array
% with as many entries as there are raw filenames.
%

% $Id:$

period = 2721;

if nargin == 2
    offset = NaN;
end

for i = 1:length(rawfilenames)
    [h d] = readEKRaw(rawfilenames{i}, 'Frequencies', freq, 'GPS', 0, 'PingRange', [0 Inf],...
        'SampleRange', [1 1]);
    num_pings = length(d.pings.power);
    fit = struct('std', ones(period,1)*1000, 'mean', zeros(period,1));
    for j = 1:period
        fit.std(j) = std( abs(d.pings.power - es60_error((1:num_pings) + j)) );
        fit.mean(j) = mean( abs(d.pings.power - es60_error((1:num_pings) + j)) );
    end
    [m, zero_error_ping(i)] = min(fit.std);
    % if there are enough pings to guarantee a change in slope in the
    % error, use the value, otherwise investigate a bit more.
    if num_pings < period/2
        std_of_std = std(fit.std);
        % check to see if the minimum is a good one
        close_values = find(fit.std < min(fit.std)+0.01*std_of_std);
        if length(close_values) > 40 % not good enough
            % use the supplied offset (or ask for one)
            if isnan(offset)
                disp('Cannot find the zero error ping number. You need to manually supply an offset')
                zero_error_ping(i) = NaN;
            else
                % use the supplied offset to deduce the zero error ping
                % number. 
                % XXX This needs some more checking to make sure it works
                [m, zero_error_ping(i)] = min(abs(fit.mean - offset));
            end
        end
    else
        mean_corrected_value = mean(d.pings.power - es60_error((1:num_pings)+zero_error_ping(i)));
        disp(['The mean corrected value is ' num2str(mean_corrected_value) ' dB'])
    end
    disp(['The zero error ping number is ' num2str(zero_error_ping(i))])
    plot(d.pings.power)
    hold on
    plot(d.pings.power - es60_error((1:num_pings)+zero_error_ping(i)), 'r')
    legend('Uncorrected', 'Corrected')
    hold off
    disp('Paused. Press any key to continue')
    pause
end




