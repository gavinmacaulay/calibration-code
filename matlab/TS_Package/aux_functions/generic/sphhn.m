% spherical hankel function
% function hn=sphhn(n,x)

function hn=sphhn(n,x)

hn=sphbeslj(n,x)+i*sphbesly(n,x);
