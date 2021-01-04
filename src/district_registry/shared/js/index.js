const Web3 = require('web3');

// import Web3 from 'web3';
import { SnackbarProvider, withSnackbar } from 'notistack';

import * as Connect0x from '@0x/connect';
import * as Contract0x from '@0x/contract-wrappers';
import * as OrderUtils0x from '@0x/order-utils';
import * as Utils0x from '@0x/utils';
import * as Web3Wrapper0x from '@0x/web3-wrapper';
import * as Subproviders0x from '@0x/subproviders';


window.Connect0x = Connect0x;
window.Contract0x = Contract0x;
window.OrderUtils0x = OrderUtils0x;
window.Utils0x = Utils0x;
window.Subproviders0x = Subproviders0x;
window.Web3Wrapper0x = Web3Wrapper0x;

window.Web3x = Web3;
window.withSnackbar = withSnackbar;
window.StackedSnackbars = SnackbarProvider;
