% start TS GUI program
% The TS package is a Graphic User Interface (GUI) based MATLAB software  
% to compute the Target Strength  (TS) of a solid elastic sphere. 

%clear all
%close all
fullname = mfilename('fullpath');
[p, name, ext] = fileparts(fullname);

% Add to path, but don't save so as to not disturb the user's permament
% paths. This adds to the top of the path so will superceed any function on
% the existing path that may have the same name.
addpath(fullfile(p, 'aux_functions'), ...
    fullfile(p, 'aux_functions\generic'), ...
    fullfile(p, 'sw_property'));

global out para

hdl=TargetStrength;

