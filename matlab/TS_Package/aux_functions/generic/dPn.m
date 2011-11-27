% Derivative of Legendre Polynomial:
% dPn(cos(theta))= dPn (n,x,flag,Pn);
%  flag = 0: compute Pn(x,n)
%       = 1: do not compute Pn, with Pn as input
function      ans=dPn(n,x,flag,pn)

if nargin < 3
  flag=0;
end

x=x(:);
m=length(x);

if ( max(abs(x)) > 1) 
  disp( '|x| must be smaller than 1')
  return
end
indx1=find(abs(x) == 1);
x(indx1)=x(indx1)-sign(x(indx1))*1e-10;

if flag == 0
pn(:,1)=ones(m,1);
pn(:,2)=x;
for i=1:max(n)-1
   pn(:,i+2)=((2*i+1)*x.*pn(:,i+1)-i*pn(:,i))/(i+1);
end

end

% do not compute Pn
dpn(:,1)=zeros(m,1);
for i=1:max(n)
   dpn(:,i+1)=i*(x.*pn(:,i+1)-pn(:,i))./(x.*x-1);
end


%% compute 
ans=dpn(:,n+1);
