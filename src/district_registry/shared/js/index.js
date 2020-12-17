const Web3 = require('web3');

// import Web3 from 'web3';
import { SnackbarProvider, withSnackbar } from 'notistack';

window.Web3x = Web3;
window.withSnackbar = withSnackbar;
window.StackedSnackbars = SnackbarProvider;
